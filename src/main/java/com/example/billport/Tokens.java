package com.example.billport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class Tokens {

	private Tokens() {
	}

	/**
	 * Constant-time comparison, so a wrong guess doesn't reveal how many
	 * leading characters were right. An unset expected token never matches.
	 */
	static boolean matches(String given, String expected) {
		if (given == null || expected == null || expected.isEmpty()) {
			return false;
		}
		return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
				expected.getBytes(StandardCharsets.UTF_8));
	}

}
