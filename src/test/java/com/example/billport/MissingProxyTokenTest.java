package com.example.billport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "app.proxy-auth-token=", "app.webhook-token=" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MissingProxyTokenTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	TestDomains testDomains;

	@BeforeEach
	void setUp() {
		testDomains.reset();
	}

	@Test
	void unsetProxyTokenRejectsEveryXServedFor() throws Exception {
		mvc.perform(get("/").header("X-Served-For", TestDomains.ACME).header("X-SaaS-Domains-Auth-Token", ""))
			.andExpect(status().isForbidden());
	}

	@Test
	void unsetWebhookTokenRejectsEveryWebhook() throws Exception {
		mvc.perform(post("/webhooks/saascustomdomains?token=").contentType("application/json")
			.content("{\"event\":\"domain_deleted\",\"domain\":{\"uuid\":\"dom_acme\"}}"))
			.andExpect(status().isUnauthorized());
	}

}
