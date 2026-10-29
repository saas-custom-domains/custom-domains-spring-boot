package com.example.billport;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings from application.properties, which reads them from the
 * environment (APP_HOSTS, APP_DOMAIN, DOMAINS_PROVIDER, ...).
 */
@ConfigurationProperties("app")
public record AppProperties(
		// Hostnames that serve the dashboard, not a customer portal.
		List<String> hosts,
		// The domain your customers can't claim, e.g. "billport.example".
		String domain,
		// The upstream's auth token, sent in X-SaaS-Domains-Auth-Token.
		String proxyAuthToken,
		// The token on your webhook in the SaaS Custom Domains dashboard.
		String webhookToken) {

}
