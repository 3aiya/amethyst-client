package com.amethystclient.accounts;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies the accounts saved in Meteor Client ({@code meteor-client/accounts.nbt}) into the account
 * list, once per account: an imported account deleted here stays deleted. Microsoft accounts from
 * Meteor were signed in through Meteor's own app, so they ask for one browser sign-in on first use.
 */
final class MeteorImport {
	private static final Logger LOGGER = LoggerFactory.getLogger("amethystclient/accounts");
	private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("meteor-client").resolve("accounts.nbt");

	private MeteorImport() {
	}

	/** Returns whether the store changed. Called with the store's lock held. */
	static boolean importInto(AccountStore store) {
		if (!Files.isRegularFile(FILE)) {
			return false;
		}
		String signature;
		try {
			signature = Files.getLastModifiedTime(FILE).toMillis() + ":" + Files.size(FILE);
		} catch (IOException e) {
			return false;
		}
		if (signature.equals(store.meteorSignature)) {
			return false;
		}
		store.meteorSignature = signature;
		List<Account> found = new ArrayList<>();
		try {
			Object accounts = Nbt.read(FILE).get("accounts");
			if (accounts instanceof List<?> list) {
				for (Object element : list) {
					if (element instanceof Map<?, ?> tag) {
						Account account = toAccount(tag);
						if (account != null && store.meteorImported.add(account.importKey())) {
							found.add(account);
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Couldn't read Meteor Client accounts from {}", FILE, e);
		}
		int added = found.isEmpty() ? 0 : store.addAll(found);
		if (added > 0) {
			LOGGER.info("Imported {} account(s) from Meteor Client", added);
		}
		return true;
	}

	private static Account toAccount(Map<?, ?> tag) {
		String name = string(tag, "name");
		String token = string(tag, "token");
		String username = "";
		String uuid = "";
		if (tag.get("cache") instanceof Map<?, ?> cache) {
			username = string(cache, "username");
			uuid = string(cache, "uuid");
		}
		return switch (string(tag, "type")) {
			case "Cracked" -> name.isBlank() ? null : Account.imported(AccountType.CRACKED, name, "", "");
			// Meteor keeps the refresh token in "name".
			case "Microsoft" -> name.isBlank() ? null : Account.imported(AccountType.MICROSOFT, username, uuid, name);
			case "Session" -> token.isBlank() ? null : Account.imported(AccountType.SESSION, username, uuid, token);
			case "TheAltening" -> {
				String alt = token.isBlank() ? name : token;
				yield alt.isBlank() ? null : Account.imported(AccountType.THE_ALTENING, username, uuid, alt);
			}
			default -> null;
		};
	}

	private static String string(Map<?, ?> tag, String key) {
		return tag.get(key) instanceof String value ? value : "";
	}

	/** Just enough of the NBT format to read a saved file: compounds become maps, lists become lists. */
	private static final class Nbt {
		private Nbt() {
		}

		static Map<String, Object> read(Path file) throws IOException {
			try (InputStream raw = new BufferedInputStream(Files.newInputStream(file))) {
				raw.mark(2);
				boolean gzip = raw.read() == 0x1f && raw.read() == 0x8b;
				raw.reset();
				DataInputStream in = new DataInputStream(gzip ? new GZIPInputStream(raw) : raw);
				if (in.readByte() != 10) {
					throw new IOException("not an NBT compound");
				}
				in.readUTF();
				return compound(in);
			}
		}

		private static Map<String, Object> compound(DataInputStream in) throws IOException {
			Map<String, Object> map = new HashMap<>();
			for (byte type = in.readByte(); type != 0; type = in.readByte()) {
				String name = in.readUTF();
				map.put(name, payload(in, type));
			}
			return map;
		}

		private static Object payload(DataInputStream in, byte type) throws IOException {
			return switch (type) {
				case 1 -> in.readByte();
				case 2 -> in.readShort();
				case 3 -> in.readInt();
				case 4 -> in.readLong();
				case 5 -> in.readFloat();
				case 6 -> in.readDouble();
				case 7 -> in.readNBytes(length(in));
				case 8 -> in.readUTF();
				case 9 -> {
					byte elementType = in.readByte();
					int length = length(in);
					List<Object> list = new ArrayList<>();
					for (int i = 0; i < length; i++) {
						list.add(payload(in, elementType));
					}
					yield list;
				}
				case 10 -> compound(in);
				case 11 -> {
					int[] values = new int[length(in)];
					for (int i = 0; i < values.length; i++) {
						values[i] = in.readInt();
					}
					yield values;
				}
				case 12 -> {
					long[] values = new long[length(in)];
					for (int i = 0; i < values.length; i++) {
						values[i] = in.readLong();
					}
					yield values;
				}
				default -> throw new IOException("unknown NBT tag type " + type);
			};
		}

		private static int length(DataInputStream in) throws IOException {
			int length = in.readInt();
			if (length < 0 || length > 1 << 24) {
				throw new IOException("bad NBT length " + length);
			}
			return length;
		}
	}
}
