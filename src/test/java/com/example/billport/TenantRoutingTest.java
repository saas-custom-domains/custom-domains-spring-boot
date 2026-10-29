package com.example.billport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantRoutingTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	TestDomains testDomains;

	@Autowired
	CopyRequestRepository copyRequests;

	@Autowired
	FilterRegistrationBean<TenantFilter> tenantFilter;

	@BeforeEach
	void setUp() {
		copyRequests.deleteAll();
		testDomains.reset();
	}

	/** A request as SaaS Custom Domains forwards it. */
	static MockHttpServletRequestBuilder viaProxy(MockHttpServletRequestBuilder request, String domain) {
		return request.header("Host", "app.billport.example")
			.header("X-Forwarded-Host", "app.billport.example")
			.header("X-Forwarded-Proto", "https")
			.header("X-Served-For", domain)
			.header("X-SaaS-Domains-IP", "203.0.113.7")
			.header("X-SaaS-Domains-Auth-Token", "test_token");
	}

	@Test
	void appHostServesTheDashboard() throws Exception {
		mvc.perform(get("/").header("Host", "localhost:3000"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Billport dashboard")));
	}

	@Test
	void activeDomainServesTheSuppliersPortal() throws Exception {
		mvc.perform(get("/").header("Host", "Billing.Acme.test:3000"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Acme Supplies")))
			.andExpect(content().string(not(containsString("Billport dashboard"))));
	}

	@Test
	void pendingAndUnknownDomainsAre404() throws Exception {
		mvc.perform(get("/").header("Host", TestDomains.GLOBEX_PENDING)).andExpect(status().isNotFound());
		mvc.perform(get("/").header("Host", "evil.example")).andExpect(status().isNotFound());
	}

	@Test
	void readsXServedForWithTheProxyToken() throws Exception {
		mvc.perform(viaProxy(get("/"), TestDomains.ACME))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Acme Supplies")))
			// ServletUriComponentsBuilder sees the customer's domain.
			.andExpect(content().string(containsString("https://billing.acme.test/")))
			.andExpect(content().string(not(containsString("app.billport.example"))));
	}

	@Test
	void rejectsXServedForWithoutTheRightToken() throws Exception {
		mvc.perform(get("/").header("X-Served-For", TestDomains.ACME)).andExpect(status().isForbidden());
		mvc.perform(get("/").header("X-Served-For", TestDomains.ACME).header("X-SaaS-Domains-Auth-Token", "guess"))
			.andExpect(status().isForbidden());
	}

	@Test
	void ignoresXForwardedHostForTenantLookup() throws Exception {
		mvc.perform(get("/").header("Host", "localhost").header("X-Forwarded-Host", TestDomains.ACME))
			.andExpect(content().string(containsString("Billport dashboard")));
	}

	@Test
	void portalRoutesAreHiddenOnTheAppHost() throws Exception {
		mvc.perform(post("/copy-requests").with(csrf())
			.header("Host", "localhost")
			.param("invoiceNumber", "INV-1042")
			.param("email", "ap@client.test")).andExpect(status().isNotFound());
		assertThat(copyRequests.count()).isZero();
	}

	@Test
	void dashboardRoutesAreHiddenOnCustomerDomains() throws Exception {
		mvc.perform(viaProxy(post("/suppliers/1/domains").with(csrf()), TestDomains.ACME).param("host", "x.test"))
			.andExpect(status().isNotFound());
		mvc.perform(viaProxy(post("/webhooks/saascustomdomains?token=hook_token"), TestDomains.ACME)
			.contentType("application/json")
			.content("{\"event\":\"domain_deleted\",\"domain\":{\"uuid\":\"dom_acme\"}}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void healthCheckSkipsTheHostCheck() throws Exception {
		mvc.perform(get("/actuator/health").header("Host", "172.18.0.3:3000")).andExpect(status().isOk());
	}

	@Test
	void copyRequestThroughTheProxyUsesTheDomainsSupplier() throws Exception {
		mvc.perform(viaProxy(post("/copy-requests").with(csrf()), TestDomains.ACME)
			.param("invoiceNumber", "INV-1042")
			.param("email", "ap@client.test"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/"))
			.andExpect(flash().attribute("message",
					"Thanks. Acme Supplies will email a copy of INV-1042 to ap@client.test."));

		var saved = copyRequests.findAll().getFirst();
		assertThat(saved.getSupplier().getSlug()).isEqualTo("acme");
		// The visitor's IP, not the proxy's.
		assertThat(saved.getClientIp()).isEqualTo("203.0.113.7");
	}

	@Test
	void anotherSuppliersInvoiceIsRejected() throws Exception {
		mvc.perform(viaProxy(post("/copy-requests").with(csrf()), TestDomains.ACME)
			.param("invoiceNumber", "GF-2026-118")
			.param("email", "ap@client.test"))
			.andExpect(flash().attribute("error", "Pick an invoice and enter a valid email."));
		assertThat(copyRequests.count()).isZero();
	}

	@Test
	void tenantFilterRunsBeforeSpringSecurity() throws Exception {
		// Spring Security's HSTS header only goes on secure requests, so its
		// presence shows Spring Security saw the wrapped request.
		mvc.perform(viaProxy(get("/"), TestDomains.ACME))
			.andExpect(header().string("Strict-Transport-Security", containsString("max-age")));
		assertThat(tenantFilter.getOrder()).isLessThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
	}

}
