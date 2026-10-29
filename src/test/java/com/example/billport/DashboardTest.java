package com.example.billport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	TestDomains testDomains;

	@MockitoBean
	SaasDomainsClient api;

	@BeforeEach
	void setUp() {
		testDomains.reset();
	}

	ResultActions add(String host, String dnsContact) throws Exception {
		return mvc.perform(post("/suppliers/1/domains").with(csrf())
			.header("Host", "localhost")
			.param("host", host)
			.param("dnsContact", dnsContact));
	}

	@Test
	void addsANormalizedDomainAndStoresItsUuid() throws Exception {
		given(api.createDomain("portal.acme.test", "it@acme.test"))
			.willReturn(new SaasDomainsClient.CreatedDomain("dom_new", "portal.acme.test", "pending_check"));

		add(" Portal.Acme.test. ", "it@acme.test").andExpect(redirectedUrl("/"))
			.andExpect(flash().attributeCount(0));

		var domain = testDomains.get("portal.acme.test");
		assertThat(domain.getProviderUuid()).isEqualTo("dom_new");
		assertThat(domain.statusLabel()).isEqualTo("Waiting for DNS");
	}

	@Test
	void rejectsOurOwnDomainsAndInvalidInput() throws Exception {
		add("admin.billport.example", "").andExpect(flash().attribute("error", "That's one of our domains."));
		add("not a domain", "").andExpect(flash().attribute("error", "That isn't a valid domain."));
		verify(api, never()).createDomain(any(), any());
	}

	@Test
	void rejectsAHostThatIsAlreadyConnected() throws Exception {
		add(TestDomains.ACME, "").andExpect(flash().attribute("error", "billing.acme.test is already connected to a portal."));
		verify(api, never()).createDomain(any(), any());
	}

	@Test
	void doesNotKeepTheRowWhenTheApiRefuses() throws Exception {
		given(api.createDomain("taken.acme.test", ""))
			.willThrow(new SaasDomainsClient.SaasDomainsException(400, "Host has already been taken", "host_taken"));

		add("taken.acme.test", "")
			.andExpect(flash().attribute("error", "Could not add the domain: Host has already been taken"));
		assertThat(testDomains.get("taken.acme.test")).isNull();
	}

	@Test
	void checkDnsAsksTheApi() throws Exception {
		var domain = testDomains.get(TestDomains.GLOBEX_PENDING);
		given(api.verifyDns("dom_globex")).willReturn(new SaasDomainsClient.DnsCheck("valid"));

		mvc.perform(post("/domains/{id}/check", domain.getId()).with(csrf()).header("Host", "localhost"))
			.andExpect(status().isFound());
		assertThat(testDomains.get(TestDomains.GLOBEX_PENDING).getStatus()).isEqualTo(CustomDomain.Status.ACTIVE);
	}

	@Test
	void removeDeletesAtTheApiThenLocally() throws Exception {
		var domain = testDomains.get(TestDomains.ACME);
		mvc.perform(post("/domains/{id}/remove", domain.getId()).with(csrf()).header("Host", "localhost"))
			.andExpect(status().isFound());
		verify(api).deleteDomain("dom_acme");
		assertThat(testDomains.get(TestDomains.ACME)).isNull();
	}

}
