package com.example.billport;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HostsTest {

	private final Hosts hosts = new Hosts(new AppProperties(List.of("localhost", "App.Billport.example"),
			"billport.example", "token", "hook"));

	@Test
	void normalizeLowercasesAndStripsPortAndTrailingDot() {
		assertThat(Hosts.normalize(" Billing.Acme.com.:443 ")).isEqualTo("billing.acme.com");
		assertThat(Hosts.normalize("billing.acme.com:3000")).isEqualTo("billing.acme.com");
		assertThat(Hosts.normalize(null)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "billing.acme.com", "acme.co.uk", "xn--bcher-kva.example" })
	void acceptsValidHosts(String host) {
		assertThat(Hosts.isValid(host)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "acme", "-acme.com", "acme..com", "ac me.com", "https://a.com", "acme.com." })
	void rejectsInvalidHosts(String host) {
		assertThat(Hosts.isValid(host)).isFalse();
	}

	@Test
	void appHostsAreNormalized() {
		assertThat(hosts.isAppHost("app.billport.example")).isTrue();
		assertThat(hosts.isAppHost("billing.acme.com")).isFalse();
	}

	@Test
	void reservesOurDomainAndItsSubdomains() {
		assertThat(hosts.isReserved("billport.example")).isTrue();
		assertThat(hosts.isReserved("admin.billport.example")).isTrue();
		assertThat(hosts.isReserved("localhost")).isTrue();
		assertThat(hosts.isReserved("notbillport.example")).isFalse();
	}

	@Test
	void tokensCompareExactly() {
		assertThat(Tokens.matches("abc", "abc")).isTrue();
		assertThat(Tokens.matches("abd", "abc")).isFalse();
		assertThat(Tokens.matches("ab", "abc")).isFalse();
		assertThat(Tokens.matches(null, "abc")).isFalse();
	}

	@Test
	void anUnsetTokenNeverMatches() {
		assertThat(Tokens.matches("", "")).isFalse();
		assertThat(Tokens.matches("", null)).isFalse();
	}

}
