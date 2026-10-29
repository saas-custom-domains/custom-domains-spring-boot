package com.example.billport;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsAndCsrfGotchaTest {

	@RestController
	static class Form {

		@PostMapping("/copy-requests")
		String post() {
			return "ok";
		}

	}

	/** A typical app-wide CORS setup: /** open to your own front end only. */
	static CorsFilter corsFilter() {
		var config = new CorsConfiguration();
		config.setAllowedOrigins(List.of("https://app.billport.example"));
		config.setAllowedMethods(List.of("GET", "POST"));
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return new CorsFilter(source);
	}

	static TenantFilter tenantFilter() {
		var suppliers = mock(SupplierRepository.class);
		given(suppliers.findByActiveHost(anyString())).willReturn(Optional.of(mock(Supplier.class)));
		var hosts = new Hosts(new AppProperties(List.of("app.billport.example"), "billport.example", "test_token", ""));
		return new TenantFilter(hosts, suppliers, "test_token");
	}

	static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder formPostFromCustomerDomain() {
		return post("/copy-requests").header("Host", "app.billport.example")
			.header("Origin", "https://billing.acme.test")
			.header("X-Forwarded-Proto", "https")
			.header("X-Served-For", "billing.acme.test")
			.header("X-SaaS-Domains-Auth-Token", "test_token");
	}

	@Test
	void corsTreatsTheSameOriginFormAsCrossOrigin() throws Exception {
		// Origin https://billing.acme.test vs. request host app.billport.example.
		MockMvcBuilders.standaloneSetup(new Form())
			.addFilters(corsFilter())
			.build()
			.perform(formPostFromCustomerDomain())
			.andExpect(status().isForbidden())
			.andExpect(content().string("Invalid CORS request"));
	}

	@Test
	void corsPassesOnceTheTenantFilterRunsFirst() throws Exception {
		MockMvcBuilders.standaloneSetup(new Form())
			.addFilters(tenantFilter(), corsFilter())
			.build()
			.perform(formPostFromCustomerDomain())
			.andExpect(status().isOk());
	}

	@Test
	void corsStillFailsIfTheTenantFilterRunsAfter() throws Exception {
		// What a @Component filter gets you: it runs after Spring Security,
		// where http.cors() lives.
		MockMvcBuilders.standaloneSetup(new Form())
			.addFilters(corsFilter(), tenantFilter())
			.build()
			.perform(formPostFromCustomerDomain())
			.andExpect(status().isForbidden());
	}

	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	@org.junit.jupiter.api.Nested
	class SpringSecurityCsrf {

		@Autowired
		MockMvc mvc;

		@Autowired
		TestDomains testDomains;

		@BeforeEach
		void setUp() {
			testDomains.reset();
		}

		@Test
		void checksTheTokenNotTheOrigin() throws Exception {
			mvc.perform(TenantRoutingTest.viaProxy(post("/copy-requests").with(csrf()), TestDomains.ACME)
				.header("Origin", "https://some-other-site.example")
				.param("invoiceNumber", "INV-1042")
				.param("email", "ap@client.test")).andExpect(status().isFound());
		}

		@Test
		void rejectsAFormWithoutTheToken() throws Exception {
			mvc.perform(TenantRoutingTest.viaProxy(post("/copy-requests"), TestDomains.ACME)
				.header("Origin", "https://billing.acme.test")
				.param("invoiceNumber", "INV-1042")
				.param("email", "ap@client.test")).andExpect(status().isForbidden());
		}

	}

}
