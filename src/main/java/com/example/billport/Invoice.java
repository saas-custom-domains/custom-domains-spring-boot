package com.example.billport;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Invoice {

	@Id
	private Long id;

	@ManyToOne(optional = false)
	private Supplier supplier;

	private String number;

	private LocalDate issuedOn;

	private BigDecimal amount;

	private String status;

	protected Invoice() {
	}

	public String getNumber() {
		return number;
	}

	public LocalDate getIssuedOn() {
		return issuedOn;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getStatus() {
		return status;
	}

}
