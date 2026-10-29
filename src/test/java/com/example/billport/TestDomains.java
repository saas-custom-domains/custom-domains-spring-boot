package com.example.billport;

import org.springframework.stereotype.Component;

/** Puts the custom domains a test needs in the database. */
@Component
class TestDomains {

	static final String ACME = "billing.acme.test";

	static final String GLOBEX_PENDING = "invoices.globex.test";

	private final CustomDomainRepository domains;

	private final SupplierRepository suppliers;

	TestDomains(CustomDomainRepository domains, SupplierRepository suppliers) {
		this.domains = domains;
		this.suppliers = suppliers;
	}

	void reset() {
		domains.deleteAll();
		var acme = new CustomDomain(suppliers.findById(1L).orElseThrow(), ACME);
		acme.setStatus(CustomDomain.Status.ACTIVE);
		acme.setProviderUuid("dom_acme");
		domains.save(acme);
		var globex = new CustomDomain(suppliers.findById(2L).orElseThrow(), GLOBEX_PENDING);
		globex.setProviderUuid("dom_globex");
		domains.save(globex);
	}

	CustomDomain get(String host) {
		return domains.findAll().stream().filter((d) -> d.getHost().equals(host)).findFirst().orElse(null);
	}

}
