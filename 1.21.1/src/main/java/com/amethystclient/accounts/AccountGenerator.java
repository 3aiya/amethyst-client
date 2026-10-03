package com.amethystclient.accounts;

import com.amethystclient.autologin.AutoLogin;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Makes cracked accounts in bulk, from random names or a pasted list. A password given for them is
 * saved for {@link AutoLogin}, which then registers and logs them in on the Amethyst servers.
 */
public final class AccountGenerator {
	public static final int MAX_COUNT = 1000;
	private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
	private static final String PASSWORD_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
	private static final String[] ADJECTIVES = {
			"Swift", "Silent", "Dark", "Lucky", "Wild", "Rapid", "Clever", "Mighty",
			"Tiny", "Brave", "Calm", "Eager", "Fuzzy", "Golden", "Happy", "Jolly",
			"Keen", "Lazy", "Merry", "Noble", "Proud", "Quiet", "Sneaky", "Witty"
	};
	private static final String[] NOUNS = {
			"Fox", "Wolf", "Bear", "Hawk", "Tiger", "Panda", "Otter", "Raven",
			"Cobra", "Falcon", "Badger", "Lynx", "Moose", "Bison", "Viper", "Heron",
			"Gecko", "Llama", "Mantis", "Newt", "Osprey", "Puma", "Sloth", "Wren"
	};

	/** How generated accounts get an auto-login password. */
	public enum PasswordMode {
		NONE("No password"),
		SAME("Same for all"),
		RANDOM("Random each");

		public final String label;

		PasswordMode(String label) {
			this.label = label;
		}
	}

	/** Names parsed from a pasted list, with "name:password" passwords kept. */
	public record ParsedList(List<String> names, List<String> passwords, int invalid, int duplicates) {
	}

	private AccountGenerator() {
	}

	public static boolean isValidName(String name) {
		return name != null && VALID_NAME.matcher(name).matches();
	}

	public static List<String> randomNames(int count) {
		int wanted = Math.max(1, Math.min(MAX_COUNT, count));
		Set<String> taken = existingNames();
		List<String> names = new ArrayList<>();
		Random random = new Random();
		for (int guard = wanted * 30; names.size() < wanted && guard > 0; guard--) {
			String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
			String noun = NOUNS[random.nextInt(NOUNS.length)];
			String name = switch (random.nextInt(4)) {
				case 0 -> adjective + noun + digits(random, 2, 3);
				case 1 -> adjective + noun;
				case 2 -> noun + digits(random, 2, 4);
				default -> adjective + noun + "_" + digits(random, 1, 2);
			};
			if (name.length() > 16) {
				name = name.substring(0, 16);
			}
			if (isValidName(name) && taken.add(name.toLowerCase(Locale.ROOT))) {
				names.add(name);
			}
		}
		return names;
	}

	/** One name per line (or separated by spaces, commas or semicolons); "name:password" is allowed. */
	public static ParsedList parseList(String raw) {
		List<String> names = new ArrayList<>();
		List<String> passwords = new ArrayList<>();
		int invalid = 0;
		int duplicates = 0;
		Set<String> taken = existingNames();
		for (String token : raw == null ? new String[0] : raw.split("[\\s,;]+")) {
			if (token.isBlank()) {
				continue;
			}
			int colon = token.indexOf(':');
			String name = (colon >= 0 ? token.substring(0, colon) : token).trim();
			String password = colon >= 0 ? token.substring(colon + 1).trim() : "";
			if (!isValidName(name)) {
				invalid++;
			} else if (!taken.add(name.toLowerCase(Locale.ROOT))) {
				duplicates++;
			} else {
				names.add(name);
				passwords.add(password);
			}
		}
		return new ParsedList(names, passwords, invalid, duplicates);
	}

	/**
	 * Saves the names as generated accounts and their passwords for auto-login. {@code passwords}
	 * (may be null) gives a password per name; where it's empty, {@code mode} decides.
	 */
	public static int add(List<String> names, List<String> passwords, PasswordMode mode, String sharedPassword) {
		List<Account> batch = new ArrayList<>();
		Map<String, String> autoLogin = new LinkedHashMap<>();
		for (int i = 0; i < names.size() && i < MAX_COUNT; i++) {
			String name = names.get(i);
			batch.add(Account.cracked(name, AccountType.GENERATED));
			String password = passwords != null && i < passwords.size() ? passwords.get(i) : "";
			if (password.isEmpty()) {
				password = switch (mode) {
					case NONE -> "";
					case SAME -> sharedPassword == null ? "" : sharedPassword.trim();
					case RANDOM -> randomPassword();
				};
			}
			if (!password.isEmpty() && !password.contains(" ")) {
				autoLogin.put(name, password);
			}
		}
		int added = AccountStore.get().addAll(batch);
		AutoLogin.rememberPasswords(autoLogin);
		return added;
	}

	private static String randomPassword() {
		SecureRandom random = new SecureRandom();
		StringBuilder password = new StringBuilder(12);
		for (int i = 0; i < 12; i++) {
			password.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
		}
		return password.toString();
	}

	private static Set<String> existingNames() {
		Set<String> names = new LinkedHashSet<>();
		for (Account account : AccountStore.get().all()) {
			if (account.type.isCracked()) {
				names.add(account.username().toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	private static String digits(Random random, int min, int max) {
		int length = min + random.nextInt(max - min + 1);
		StringBuilder out = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			out.append(random.nextInt(10));
		}
		return out.toString();
	}
}
