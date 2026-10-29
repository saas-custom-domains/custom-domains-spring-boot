package com.example.billport;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Your own dashboard, on APP_HOSTS only. This example has no login: in
 * your app, check that the signed-in user owns the supplier and the domain
 * in every handler.
 */
@Controller
class DashboardController {

	private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

	private final SupplierRepository suppliers;

	private final CustomDomainRepository domains;

	private final DomainService domainService;

	private final Hosts hosts;

	DashboardController(SupplierRepository suppliers, CustomDomainRepository domains, DomainService domainService,
			Hosts hosts) {
		this.suppliers = suppliers;
		this.domains = domains;
		this.domainService = domainService;
		this.hosts = hosts;
	}

	@GetMapping("/")
	String dashboard(Model model) {
		Map<Long, List<CustomDomain>> bySupplier = domains.findAllByOrderByIdAsc()
			.stream()
			.collect(Collectors.groupingBy((d) -> d.getSupplier().getId()));
		model.addAttribute("suppliers", suppliers.findAll());
		model.addAttribute("domains", bySupplier);
		return "dashboard";
	}

	@PostMapping("/suppliers/{id}/domains")
	String addDomain(@PathVariable long id, @RequestParam String host,
			@RequestParam(defaultValue = "") String dnsContact, RedirectAttributes flash) {
		Supplier supplier = suppliers.findById(id).orElseThrow();
		String normalized = Hosts.normalize(host);
		String error = null;
		if (!Hosts.isValid(normalized)) {
			error = "That isn't a valid domain.";
		}
		else if (hosts.isReserved(normalized)) {
			error = "That's one of our domains.";
		}
		else {
			try {
				domainService.add(supplier, normalized, dnsContact);
			}
			catch (DomainService.DomainTakenException e) {
				error = e.getMessage();
			}
			catch (SaasDomainsClient.SaasDomainsException e) {
				log.warn("Could not add {}: {} {}", normalized, e.status(), e.errorCode());
				error = "Could not add the domain: " + e.getMessage();
			}
		}
		if (error != null) {
			flash.addFlashAttribute("errorFor", id);
			flash.addFlashAttribute("error", error);
		}
		return "redirect:/";
	}

	@PostMapping("/domains/{id}/check")
	String checkDomain(@PathVariable long id) {
		domains.findById(id).ifPresent(domainService::check);
		return "redirect:/";
	}

	@PostMapping("/domains/{id}/remove")
	String removeDomain(@PathVariable long id) {
		domains.findById(id).ifPresent(domainService::remove);
		return "redirect:/";
	}

}
