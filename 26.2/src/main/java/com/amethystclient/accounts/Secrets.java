package com.amethystclient.accounts;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encrypts account tokens (AES-GCM) before they're written to disk, with a random key kept in its
 * own file. A copied or shared accounts file then doesn't hand out working tokens on its own.
 */
final class Secrets {
	private static final Logger LOGGER = LoggerFactory.getLogger("amethystclient/accounts");
	private static final Path KEY_FILE = FabricLoader.getInstance().getConfigDir().resolve("amethystclient-accounts.key");
	private static final String AES_PREFIX = "aes:";
	private static final String PLAIN_PREFIX = "plain:";
	private static final int NONCE_BYTES = 12;
	private static final SecureRandom RANDOM = new SecureRandom();

	private static byte[] key;

	private Secrets() {
	}

	static String seal(String plain) {
		if (plain == null || plain.isEmpty()) {
			return "";
		}
		byte[] secretKey = key();
		if (secretKey == null) {
			return PLAIN_PREFIX + plain;
		}
		try {
			byte[] nonce = new byte[NONCE_BYTES];
			RANDOM.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, "AES"), new GCMParameterSpec(128, nonce));
			byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = Arrays.copyOf(nonce, NONCE_BYTES + sealed.length);
			System.arraycopy(sealed, 0, out, NONCE_BYTES, sealed.length);
			return AES_PREFIX + Base64.getEncoder().encodeToString(out);
		} catch (Exception e) {
			LOGGER.warn("Couldn't encrypt an account token; storing it unencrypted", e);
			return PLAIN_PREFIX + plain;
		}
	}

	/** The stored value decrypted, or "" when it can't be (for example the key file was deleted). */
	static String open(String stored) {
		if (stored == null || stored.isEmpty()) {
			return "";
		}
		if (stored.startsWith(PLAIN_PREFIX)) {
			return stored.substring(PLAIN_PREFIX.length());
		}
		if (!stored.startsWith(AES_PREFIX)) {
			return stored;
		}
		byte[] secretKey = key();
		if (secretKey == null) {
			return "";
		}
		try {
			byte[] data = Base64.getDecoder().decode(stored.substring(AES_PREFIX.length()));
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey, "AES"), new GCMParameterSpec(128, data, 0, NONCE_BYTES));
			return new String(cipher.doFinal(data, NONCE_BYTES, data.length - NONCE_BYTES), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Couldn't decrypt an account token (was {} replaced?)", KEY_FILE.getFileName());
			return "";
		}
	}

	private static synchronized byte[] key() {
		if (key != null) {
			return key;
		}
		try {
			if (Files.isRegularFile(KEY_FILE)) {
				byte[] existing = Files.readAllBytes(KEY_FILE);
				if (existing.length == 32) {
					return key = existing;
				}
				LOGGER.warn("{} is damaged; making a new key", KEY_FILE.getFileName());
			}
			byte[] created = new byte[32];
			RANDOM.nextBytes(created);
			Files.createDirectories(KEY_FILE.getParent());
			Path tmp = KEY_FILE.resolveSibling(KEY_FILE.getFileName() + ".tmp");
			Files.write(tmp, created);
			ownerOnly(tmp);
			Files.move(tmp, KEY_FILE, StandardCopyOption.REPLACE_EXISTING);
			return key = created;
		} catch (Exception e) {
			LOGGER.error("Couldn't read or create {}", KEY_FILE, e);
			return null;
		}
	}

	private static void ownerOnly(Path file) {
		try {
			if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
				Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
			}
		} catch (Exception ignored) {
			// Best effort; Windows keeps the user profile private anyway.
		}
	}
}
