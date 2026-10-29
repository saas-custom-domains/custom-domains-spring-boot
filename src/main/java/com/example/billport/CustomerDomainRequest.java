package com.example.billport;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * A request that came through SaaS Custom Domains (token already checked),
 * as the browser sent it: the customer's domain instead of your upstream
 * host, and the visitor's IP instead of the proxy's.
 *
 * Spring Security's CORS check, ServletUriComponentsBuilder and anything
 * else that calls getServerName() or getScheme() now see the customer's
 * domain. Tomcat's own absolute redirects don't (they use Tomcat's request,
 * not this one), which is why server.tomcat.use-relative-redirects is on.
 */
class CustomerDomainRequest extends HttpServletRequestWrapper {

	private final String host;

	private final String scheme;

	CustomerDomainRequest(HttpServletRequest request, String host) {
		super(request);
		this.host = host;
		// The proxy terminates TLS and says so in X-Forwarded-Proto. With
		// server.forward-headers-strategy set, Spring or Tomcat may have
		// read (and removed) that header already: then isSecure() knows.
		boolean https = "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")) || request.isSecure();
		this.scheme = https ? "https" : "http";
	}

	@Override
	public String getServerName() {
		return host;
	}

	@Override
	public int getServerPort() {
		return isSecure() ? 443 : 80;
	}

	@Override
	public String getScheme() {
		return scheme;
	}

	@Override
	public boolean isSecure() {
		return scheme.equals("https");
	}

	@Override
	public StringBuffer getRequestURL() {
		return new StringBuffer(scheme).append("://").append(host).append(getRequestURI());
	}

	@Override
	public String getHeader(String name) {
		return name.equalsIgnoreCase("Host") ? host : super.getHeader(name);
	}

	@Override
	public Enumeration<String> getHeaders(String name) {
		return name.equalsIgnoreCase("Host") ? Collections.enumeration(List.of(host)) : super.getHeaders(name);
	}

	@Override
	public String getRemoteAddr() {
		String ip = super.getHeader("X-SaaS-Domains-IP");
		return ip != null ? ip : super.getRemoteAddr();
	}

}
