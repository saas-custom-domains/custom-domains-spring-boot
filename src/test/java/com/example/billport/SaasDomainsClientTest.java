package com.example.billport;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(value = SaasDomainsClient.class,
		properties = { "scd.api-url=https://api.test", "scd.api-token=t0k", "scd.account-uuid=acc",
				"scd.upstream-uuid=up" })
class SaasDomainsClientTest {

	static final String BASE = "https://api.test/api/v1/accounts/acc/upstreams/up/custom_domains";

	@Autowired
	SaasDomainsClient client;

	@Autowired
	MockRestServiceServer server;

	@Test
	void createsADomainWithTheDnsContact() {
		server.expect(requestTo(BASE))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer t0k"))
			.andExpect(content().json("{\"host\":\"billing.acme.com\",\"instructions_recipient\":\"it@acme.com\"}", 
					org.springframework.test.json.JsonCompareMode.STRICT))
			.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"uuid\":\"dom_1\",\"host\":\"billing.acme.com\",\"status\":\"pending_check\"}"));

		assertThat(client.createDomain("billing.acme.com", "it@acme.com").uuid()).isEqualTo("dom_1");
		server.verify();
	}

	@Test
	void leavesOutAnEmptyDnsContact() {
		server.expect(requestTo(BASE))
			.andExpect(content().json("{\"host\":\"billing.acme.com\"}", org.springframework.test.json.JsonCompareMode.STRICT))
			.andRespond(withSuccess("{\"uuid\":\"dom_1\"}", MediaType.APPLICATION_JSON));
		client.createDomain("billing.acme.com", "");
		server.verify();
	}

	@Test
	void turnsApiErrorsIntoExceptions() {
		server.expect(requestTo(BASE))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"Host has already been taken\",\"error_code\":\"host_taken\"}"));

		assertThatThrownBy(() -> client.createDomain("billing.acme.com", null))
			.isInstanceOfSatisfying(SaasDomainsClient.SaasDomainsException.class, (e) -> {
				assertThat(e.status()).isEqualTo(400);
				assertThat(e.errorCode()).isEqualTo("host_taken");
				assertThat(e.getMessage()).isEqualTo("Host has already been taken");
			});
	}

	@Test
	void anEmpty401IsAnExceptionToo() {
		server.expect(requestTo(BASE)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		assertThatThrownBy(() -> client.createDomain("billing.acme.com", null))
			.hasMessage("SaaS Custom Domains API 401");
	}

	@Test
	void verifiesDns() {
		server.expect(requestTo(BASE + "/dom_1/verify_dns_records"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{\"message\":\"DNS records verification complete.\",\"dns_status\":\"valid\"}",
					MediaType.APPLICATION_JSON));
		assertThat(client.verifyDns("dom_1").dnsStatus()).isEqualTo("valid");
	}

	@Test
	void deletingADomainThatIsAlreadyGoneIsFine() {
		server.expect(requestTo(BASE + "/dom_1")).andExpect(method(HttpMethod.DELETE))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));
		client.deleteDomain("dom_1");
		server.verify();
	}

	@Test
	void otherDeleteErrorsAreThrown() {
		server.expect(requestTo(BASE + "/dom_1")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		assertThatThrownBy(() -> client.deleteDomain("dom_1"))
			.isInstanceOf(SaasDomainsClient.SaasDomainsException.class);
	}

}
