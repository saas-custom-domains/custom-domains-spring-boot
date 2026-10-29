package com.example.billport;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * A small client for the SaaS Custom Domains API.
 * Docs: https://docs.saascustomdomains.com/custom-domains
 */
@Component
public class SaasDomainsClient {

	public record CreatedDomain(String uuid, String host, String status) {
	}

	public record DnsCheck(@JsonProperty("dns_status") String dnsStatus) {
	}

	record ApiError(String error, @JsonProperty("error_code") String errorCode) {
	}

	private final RestClient http;

	public SaasDomainsClient(RestClient.Builder builder,
			@Value("${scd.api-url}") String apiUrl,
			@Value("${scd.api-token}") String apiToken,
			@Value("${scd.account-uuid}") String accountUuid,
			@Value("${scd.upstream-uuid}") String upstreamUuid) {
		this.http = builder
			.baseUrl(apiUrl + "/api/v1/accounts/" + accountUuid + "/upstreams/" + upstreamUuid + "/custom_domains")
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
			.build();
	}

	public CreatedDomain createDomain(String host, String instructionsRecipient) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("host", host);
		if (instructionsRecipient != null && !instructionsRecipient.isBlank()) {
			// We email this address the exact DNS records to add.
			body.put("instructions_recipient", instructionsRecipient);
		}
		try {
			return http.post().body(body).retrieve().body(CreatedDomain.class);
		}
		catch (RestClientResponseException e) {
			throw SaasDomainsException.from(e);
		}
	}

	public DnsCheck verifyDns(String uuid) {
		try {
			return http.post().uri("/{uuid}/verify_dns_records", uuid).retrieve().body(DnsCheck.class);
		}
		catch (RestClientResponseException e) {
			throw SaasDomainsException.from(e);
		}
	}

	public void deleteDomain(String uuid) {
		try {
			http.delete().uri("/{uuid}", uuid).retrieve().toBodilessEntity();
		}
		catch (HttpClientErrorException.NotFound e) {
			// Already gone is fine: the goal is that it doesn't exist.
		}
		catch (RestClientResponseException e) {
			throw SaasDomainsException.from(e);
		}
	}

	/** The API answered with an error, e.g. 400 host_taken or 401. */
	public static class SaasDomainsException extends RuntimeException {

		private final int status;

		private final String errorCode;

		SaasDomainsException(int status, String message, String errorCode) {
			super(message);
			this.status = status;
			this.errorCode = errorCode;
		}

		static SaasDomainsException from(RestClientResponseException e) {
			ApiError body = null;
			try {
				body = e.getResponseBodyAs(ApiError.class);
			}
			catch (RuntimeException ignored) {
				// Not JSON (a 401 has an empty body).
			}
			String message = body != null && body.error() != null ? body.error()
					: "SaaS Custom Domains API " + e.getStatusCode().value();
			return new SaasDomainsException(e.getStatusCode().value(), message,
					body != null ? body.errorCode() : null);
		}

		public int status() {
			return status;
		}

		public String errorCode() {
			return errorCode;
		}

	}

}
