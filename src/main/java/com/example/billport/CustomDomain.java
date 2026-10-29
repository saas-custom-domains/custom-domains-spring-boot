package com.example.billport;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class CustomDomain {

	public enum Status {
		PENDING, ACTIVE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	private Supplier supplier;

	// Normalized (see Hosts.normalize), with a unique index.
	private String host;

	@Enumerated(EnumType.STRING)
	private Status status = Status.PENDING;

	private boolean httpsReady;

	// The domain's UUID at SaaS Custom Domains.
	private String providerUuid;

	private Instant createdAt = Instant.now();

	protected CustomDomain() {
	}

	public CustomDomain(Supplier supplier, String host) {
		this.supplier = supplier;
		this.host = host;
	}

	public String statusLabel() {
		if (status != Status.ACTIVE) {
			return "Waiting for DNS";
		}
		return httpsReady ? "Live" : "DNS OK, waiting for HTTPS";
	}

	public Long getId() {
		return id;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public String getHost() {
		return host;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public boolean isHttpsReady() {
		return httpsReady;
	}

	public void setHttpsReady(boolean httpsReady) {
		this.httpsReady = httpsReady;
	}

	public String getProviderUuid() {
		return providerUuid;
	}

	public void setProviderUuid(String providerUuid) {
		this.providerUuid = providerUuid;
	}

}
