package com.example.billport;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Finds the supplier from the request's hostname. Registered in WebConfig,
 * before Spring Security, so every later filter sees the customer's domain.
 */
public class TenantFilter extends OncePerRequestFilter {

	/** Request attribute with the Supplier whose portal this request is for. */
	public static final String SUPPLIER = "supplier";

	private final Hosts hosts;

	private final SupplierRepository suppliers;

	private final String proxyAuthToken;

	public TenantFilter(Hosts hosts, SupplierRepository suppliers, String proxyAuthToken) {
		this.hosts = hosts;
		this.suppliers = suppliers;
		this.proxyAuthToken = proxyAuthToken;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// Load balancers and Docker call the health check by IP address.
		return request.getRequestURI().equals("/actuator/health");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// Behind SaaS Custom Domains, Host is your own hostname and the
		// customer's domain arrives in X-Served-For. Only trust it with the
		// upstream's auth token, or anyone could claim any customer's domain.
		String servedFor = request.getHeader("X-Served-For");
		if (servedFor != null
				&& !Tokens.matches(request.getHeader("X-SaaS-Domains-Auth-Token"), proxyAuthToken)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		// The Host header, not getServerName(): with
		// server.forward-headers-strategy=framework, getServerName() is
		// whatever X-Forwarded-Host the client sent.
		String host = Hosts.normalize(servedFor != null ? servedFor : hostHeader(request));
		if (hosts.isAppHost(host)) {
			chain.doFilter(request, response);
			return;
		}

		Supplier supplier = suppliers.findByActiveHost(host).orElse(null);
		if (supplier == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown domain");
			return;
		}
		request.setAttribute(SUPPLIER, supplier);

		// The token proved the request came through the proxy, so from here
		// on the request reports the customer's domain, scheme and IP.
		chain.doFilter(servedFor != null ? new CustomerDomainRequest(request, host) : request, response);
	}

	private static String hostHeader(HttpServletRequest request) {
		String host = request.getHeader("Host");
		// No Host header: HTTP/1.0, or a MockMvc test.
		return host != null ? host : request.getServerName();
	}

}
