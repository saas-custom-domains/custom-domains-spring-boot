package com.example.billport;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

	// The one lookup every customer-facing request depends on. Keep the
	// unique index on custom_domain.host (see schema.sql).
	@Query("""
			select d.supplier from CustomDomain d
			where d.host = :host and d.status = com.example.billport.CustomDomain.Status.ACTIVE""")
	Optional<Supplier> findByActiveHost(String host);

}
