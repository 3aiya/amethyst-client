package com.amethystclient.accounts;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The saved accounts, in {@code config/amethystclient-accounts.json}. Tokens are encrypted (see
 * {@link Secrets}). Writes happen on a background thread; the newest snapshot always wins.
 */
public final class AccountStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("amethystclient/accounts");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("amethystclient-accounts.json");
	private static final AccountStore INSTANCE = new AccountStore();

	private final List<Account> accounts = new ArrayList<>();
	private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "amethystclient-accounts-save");
		thread.setDaemon(true);
		return thread;
	});
	private boolean loaded;
	private long revision;
	private long writtenRevision;

	// What has already been imported from Meteor Client, so deleted accounts don't come back.
	String meteorSignature = "";
	final Set<String> meteorImported = new LinkedHashSet<>();

	private AccountStore() {
	}

	public static AccountStore get() {
		INSTANCE.ensureLoaded();
		return INSTANCE;
	}

	private synchronized void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		read();
		if (MeteorImport.importInto(this)) {
			save();
		}
	}

	/** Changes on every edit, so screens can tell when to rebuild their lists. */
	public synchronized long revision() {
		return revision;
	}

	public synchronized List<Account> all() {
		return new ArrayList<>(accounts);
	}

	public synchronized boolean isEmpty() {
		return accounts.isEmpty();
	}

	public synchronized Account findSame(Account account) {
		for (Account saved : accounts) {
			if (saved != account && saved.sameAs(account)) {
				return saved;
			}
		}
		return null;
	}

	/** Adds the account unless it's already saved; returns whether it was added. */
	public synchronized boolean add(Account account) {
		if (findSame(account) != null) {
			return false;
		}
		accounts.add(account);
		save();
		return true;
	}

	/** Adds the accounts that aren't saved yet; returns how many were added. */
	public synchronized int addAll(List<Account> batch) {
		int added = 0;
		for (Account account : batch) {
			if (findSame(account) == null) {
				accounts.add(account);
				added++;
			}
		}
		if (added > 0) {
			save();
		}
		return added;
	}

	public synchronized boolean remove(Account account) {
		boolean removed = accounts.remove(account);
		if (removed) {
			save();
		}
		return removed;
	}

	/** Removes every account matching {@code filter}; returns how many. */
	public synchronized int removeIf(Predicate<Account> filter) {
		int removed = 0;
		for (Iterator<Account> it = accounts.iterator(); it.hasNext(); ) {
			if (filter.test(it.next())) {
				it.remove();
				removed++;
			}
		}
		if (removed > 0) {
			save();
		}
		return removed;
	}

	/** Renames a cracked account; false if the name is taken by another cracked account. */
	public synchronized boolean rename(Account account, String newName) {
		String name = newName.trim();
		for (Account saved : accounts) {
			if (saved != account && saved.type.isCracked() && saved.username().equalsIgnoreCase(name)) {
				return false;
			}
		}
		account.rename(name);
		save();
		return true;
	}

	/** Saves the current state (also after an account refreshed its tokens). */
	public void save() {
		String json;
		long saving;
		synchronized (this) {
			revision++;
			saving = revision;
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			JsonArray list = new JsonArray();
			for (Account account : accounts) {
				list.add(account.toJson());
			}
			root.add("accounts", list);
			JsonObject meteor = new JsonObject();
			meteor.addProperty("signature", meteorSignature);
			JsonArray imported = new JsonArray();
			meteorImported.forEach(imported::add);
			meteor.add("imported", imported);
			root.add("meteorImport", meteor);
			json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
		}
		writer.execute(() -> write(json, saving));
	}

	private void write(String json, long saving) {
		synchronized (this) {
			if (saving <= writtenRevision) {
				return;
			}
			writtenRevision = saving;
		}
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
			try {
				Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOGGER.error("Couldn't save {}", FILE, e);
		}
	}

	private void read() {
		if (!Files.isRegularFile(FILE)) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			if (root.has("accounts")) {
				for (JsonElement element : root.getAsJsonArray("accounts")) {
					Account account = element.isJsonObject() ? Account.fromJson(element.getAsJsonObject()) : null;
					if (account != null) {
						accounts.add(account);
					}
				}
			}
			if (root.has("meteorImport")) {
				JsonObject meteor = root.getAsJsonObject("meteorImport");
				meteorSignature = meteor.has("signature") ? meteor.get("signature").getAsString() : "";
				if (meteor.has("imported")) {
					meteor.getAsJsonArray("imported").forEach(key -> meteorImported.add(key.getAsString()));
				}
			}
		} catch (Exception e) {
			// Keep the unreadable file instead of overwriting it with an empty list.
			Path backup = FILE.resolveSibling(FILE.getFileName() + ".broken");
			LOGGER.error("Couldn't read {}; moved it to {}", FILE, backup.getFileName(), e);
			try {
				Files.move(FILE, backup, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// Nothing more to do; the next save replaces it.
			}
		}
	}
}
