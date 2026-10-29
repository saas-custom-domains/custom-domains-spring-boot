package com.example.billport;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** A client asked for a copy of an invoice on the supplier's portal. */
@Entity
public class CopyRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	private Supplier supplier;

	private String invoiceNumber;

	private String email;

	private String clientIp;

	private Instant createdAt = Instant.now();

	protected CopyRequest() {
	}

	public CopyRequest(Supplier supplier, String invoiceNumber, String email, String clientIp) {
		this.supplier = supplier;
		this.invoiceNumber = invoiceNumber;
		this.email = email;
		this.clientIp = clientIp;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public String getInvoiceNumber() {
		return invoiceNumber;
	}

	public String getEmail() {
		return email;
	}

	public String getClientIp() {
		return clientIp;
	}

}
