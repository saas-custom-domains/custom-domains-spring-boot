package com.example.billport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	TestDomains testDomains;

	@BeforeEach
	void setUp() {
		testDomains.reset();
	}

	ResultActions send(String token, String event, String uuid) throws Exception {
		// Webhooks from the mock arrive on an internal hostname (APP_HOSTS).
		return mvc.perform(post("/webhooks/saascustomdomains").param("token", token)
			.header("Host", "localhost:3000")
			.contentType("application/json")
			.content("{\"event\":\"%s\",\"domain\":{\"uuid\":\"%s\",\"host\":\"x\"}}".formatted(event, uuid)));
	}

	@Test
	void wrongTokenIs401() throws Exception {
		send("guess", "domain_dns_proxy_record_valid", "dom_globex").andExpect(status().isUnauthorized());
		assertThat(testDomains.get(TestDomains.GLOBEX_PENDING).getStatus()).isEqualTo(CustomDomain.Status.PENDING);
	}

	@Test
	void dnsAndCertificateEventsMakeADomainLive() throws Exception {
		send("hook_token", "domain_dns_proxy_record_valid", "dom_globex").andExpect(status().isNoContent());
		assertThat(testDomains.get(TestDomains.GLOBEX_PENDING).statusLabel()).isEqualTo("DNS OK, waiting for HTTPS");

		send("hook_token", "tls_certificate_issued", "dom_globex").andExpect(status().isNoContent());
		assertThat(testDomains.get(TestDomains.GLOBEX_PENDING).statusLabel()).isEqualTo("Live");

		// Sent again: same result.
		send("hook_token", "domain_dns_proxy_record_valid", "dom_globex").andExpect(status().isNoContent());
		assertThat(testDomains.get(TestDomains.GLOBEX_PENDING).statusLabel()).isEqualTo("Live");

		send("hook_token", "domain_dns_proxy_record_invalid", "dom_globex").andExpect(status().isNoContent());
		assertThat(testDomains.get(TestDomains.GLOBEX_PENDING).statusLabel()).isEqualTo("Waiting for DNS");
	}

	@Test
	void domainDeletedRemovesTheRow() throws Exception {
		send("hook_token", "domain_deleted", "dom_acme").andExpect(status().isNoContent());
		assertThat(testDomains.get(TestDomains.ACME)).isNull();
	}

	@Test
	void unknownDomainsAreAcknowledged() throws Exception {
		send("hook_token", "domain_dns_proxy_record_valid", "dom_unknown").andExpect(status().isNoContent());
	}

}
