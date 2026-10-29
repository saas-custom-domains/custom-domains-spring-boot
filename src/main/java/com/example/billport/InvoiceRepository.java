package com.example.billport;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

	List<Invoice> findBySupplierOrderByIssuedOnDesc(Supplier supplier);

	Optional<Invoice> findBySupplierAndNumber(Supplier supplier, String number);

}
