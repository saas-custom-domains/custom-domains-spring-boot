package com.example.billport;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** A Billport customer. Its clients see its invoices on its portal. */
@Entity
public class Supplier {

	@Id
	private Long id;

	private String slug;

	private String name;

	private String tagline;

	protected Supplier() {
	}

	public Long getId() {
		return id;
	}

	public String getSlug() {
		return slug;
	}

	public String getName() {
		return name;
	}

	public String getTagline() {
		return tagline;
	}

}
