package com.amethystclient.accounts;

public enum AccountType {
	MICROSOFT("Microsoft"),
	CRACKED("Cracked"),
	SESSION("Session"),
	THE_ALTENING("TheAltening"),
	/** Cracked accounts made by the generator; kept apart so they can be filtered and cleared together. */
	GENERATED("Generated");

	public final String label;

	AccountType(String label) {
		this.label = label;
	}

	/** Offline-mode accounts: just a name, no token. */
	public boolean isCracked() {
		return this == CRACKED || this == GENERATED;
	}
}
