package com.example.billport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real HTTP against the app on a random port, with the headers SaaS Custom
 * Domains adds. For what MockMvc can't show: Tomcat's own redirects and
 * Tomcat's RemoteIpValve.
 */
final class ProxiedHttp {

	private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

	private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	private final int port;

	ProxiedHttp(int port) {
		this.port = port;
	}

	HttpRequest.Builder request(String path, String servedFor) {
		var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.header("X-Forwarded-Proto", "https")
			.header("X-Forwarded-Host", "app.billport.example")
			.header("X-Forwarded-For", "203.0.113.7");
		if (servedFor != null) {
			builder.header("X-Served-For", servedFor)
				.header("X-SaaS-Domains-IP", "203.0.113.7")
				.header("X-SaaS-Domains-Auth-Token", "test_token");
		}
		return builder;
	}

	HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Loads the portal, then posts its form, like a browser would. */
	HttpResponse<String> postCopyRequest(String domain) throws Exception {
		var page = send(request("/", domain));
		String cookie = page.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
		Matcher token = CSRF.matcher(page.body());
		if (!token.find()) {
			throw new AssertionError("no CSRF token on the page");
		}
		return send(request("/copy-requests", domain).header("Cookie", cookie)
			.header("Origin", "https://" + domain)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers
				.ofString("invoiceNumber=INV-1042&email=ap%40client.test&_csrf=" + token.group(1))));
	}

}
