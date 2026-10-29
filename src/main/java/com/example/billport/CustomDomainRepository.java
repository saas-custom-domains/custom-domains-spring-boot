package com.example.billport;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomDomainRepository extends JpaRepository<CustomDomain, Long> {

	List<CustomDomain> findAllByOrderByIdAsc();

	Optional<CustomDomain> findByProviderUuid(String providerUuid);

	boolean existsByHostAndStatus(String host, CustomDomain.Status status);

}
