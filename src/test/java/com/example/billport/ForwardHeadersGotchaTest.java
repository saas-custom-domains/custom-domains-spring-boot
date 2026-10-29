package com.example.billport;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * What server.forward-headers-strategy does with the proxy's headers. Both
 * strategies give you X-Forwarded-Host, which is your own hostname; neither
 * reads X-Served-For.
 */
class ForwardHeadersGotchaTest {

	@Nested
	@SpringBootTest(properties = "server.forward-headers-strategy=framework")
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	@Import(UrlEchoController.class)
	class Framework {

		@Autowired
		MockMvc mvc;

		@Test
		void trustsXForwardedHostFromAnyone() throws Exception {
			mvc.perform(get("/test/base-url").header("Host", "localhost")
				.header("X-Forwarded-Host", "evil.example")
				.header("X-Forwarded-Proto", "https")
				.with((request) -> {
					request.setRemoteAddr("198.51.100.23");
					return request;
				})).andExpect(content().string("https://evil.example"));
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
			properties = "server.forward-headers-strategy=native")
	@ActiveProfiles("test")
	@Import(UrlEchoController.class)
	class Native {

		@LocalServerPort
		int port;

		@Test
		void trustsPrivateAddressesAndGivesYourOwnHostname() throws Exception {
			var http = new ProxiedHttp(port);
			// From 127.0.0.1, which is in server.tomcat.remoteip.internal-proxies.
			assertThat(http.send(http.request("/test/base-url", null)).body())
				.isEqualTo("https://app.billport.example");
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
			properties = { "server.forward-headers-strategy=native",
					"server.tomcat.remoteip.internal-proxies=10\\.9\\.9\\.9" })
	@ActiveProfiles("test")
	@Import(UrlEchoController.class)
	class NativeFromAnUnlistedAddress {

		@LocalServerPort
		int port;

		@Test
		void ignoresTheHeaders() throws Exception {
			var http = new ProxiedHttp(port);
			assertThat(http.send(http.request("/test/base-url", null)).body())
				.isEqualTo("http://localhost:" + port);
		}

	}

}
