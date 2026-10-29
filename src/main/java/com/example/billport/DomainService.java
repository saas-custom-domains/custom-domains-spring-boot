package com.example.billport;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Add, check and remove domains: our row first, then SaaS Custom Domains. */
@Service
public class DomainService {

	/** The host is already connected to a supplier (unique index). */
	public static class DomainTakenException extends RuntimeException {

		DomainTakenException(String host) {
			super(host + " is already connected to a portal.");
		}

	}

	private final CustomDomainRepository domains;

	private final SaasDomainsClient api;

	public DomainService(CustomDomainRepository domains, SaasDomainsClient api) {
		this.domains = domains;
		this.api = api;
	}

	public CustomDomain add(Supplier supplier, String host, String dnsContact) {
		CustomDomain domain;
		try {
			domain = domains.saveAndFlush(new CustomDomain(supplier, host));
		}
		catch (DataIntegrityViolationException e) {
			throw new DomainTakenException(host);
		}
		try {
			domain.setProviderUuid(api.createDomain(host, dnsContact).uuid());
			return domains.save(domain);
		}
		catch (RuntimeException e) {
			// Don't keep a row the proxy doesn't know about.
			domains.delete(domain);
			throw e;
		}
	}

	/** Asks SaaS Custom Domains to check the DNS records now. */
	public boolean check(CustomDomain domain) {
		boolean valid = "valid".equals(api.verifyDns(domain.getProviderUuid()).dnsStatus());
		domain.setStatus(valid ? CustomDomain.Status.ACTIVE : CustomDomain.Status.PENDING);
		domains.save(domain);
		return valid;
	}

	public void remove(CustomDomain domain) {
		// Proxy first, database second: if the API call fails, the row is
		// still here and the user can retry.
		if (domain.getProviderUuid() != null) {
			api.deleteDomain(domain.getProviderUuid());
		}
		domains.delete(domain);
	}

}
