package com.example.billport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "redirect:/" after the portal form. Spring Boot sets Tomcat's
 * useRelativeRedirects to false, and Tomcat then builds an absolute
 * Location from its own request: your upstream host, whatever the
 * request wrapper says.
 */
class RedirectGotchaTest {

	@Nested
	@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
	@ActiveProfiles("test")
	class WithRelativeRedirects {

		@LocalServerPort
		int port;

		@Autowired
		TestDomains testDomains;

		@BeforeEach
		void setUp() {
			testDomains.reset();
		}

		@Test
		void theBrowserStaysOnTheCustomersDomain() throws Exception {
			var response = new ProxiedHttp(port).postCopyRequest(TestDomains.ACME);
			assertThat(response.statusCode()).isEqualTo(302);
			assertThat(response.headers().firstValue("Location")).hasValue("/");
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
			properties = "server.tomcat.use-relative-redirects=false")
	@ActiveProfiles("test")
	class WithSpringBootsDefault {

		@LocalServerPort
		int port;

		@Autowired
		TestDomains testDomains;

		@BeforeEach
		void setUp() {
			testDomains.reset();
		}

		@Test
		void tomcatRedirectsToTheUpstreamHost() throws Exception {
			var response = new ProxiedHttp(port).postCopyRequest(TestDomains.ACME);
			assertThat(response.statusCode()).isEqualTo(302);
			// The Host this request arrived with, not billing.acme.test.
			assertThat(response.headers().firstValue("Location")).hasValue("http://localhost:" + port + "/");
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
			properties = { "server.tomcat.use-relative-redirects=false", "server.forward-headers-strategy=native" })
	@ActiveProfiles("test")
	class WithNativeForwardedHeaders {

		@LocalServerPort
		int port;

		@Autowired
		TestDomains testDomains;

		@BeforeEach
		void setUp() {
			testDomains.reset();
		}

		@Test
		void tomcatRedirectsToXForwardedHost() throws Exception {
			var http = new ProxiedHttp(port);
			var page = http.send(http.request("/", TestDomains.ACME));
			// Trusting X-Forwarded-Proto makes the session cookie Secure. Fine
			// over HTTPS; over the mock's plain HTTP the browser drops it.
			assertThat(page.headers().firstValue("Set-Cookie").orElseThrow()).contains("Secure");

			var response = http.postCopyRequest(TestDomains.ACME);
			assertThat(response.headers().firstValue("Location")).hasValue("https://app.billport.example/");
		}

	}

}
