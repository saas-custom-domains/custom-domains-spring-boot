package com.example.billport;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** A supplier's client portal, served on the supplier's own domain. */
@Controller
@CustomerSite
class PortalController {

	private static final Pattern EMAIL = Pattern.compile("^\\S+@\\S+\\.\\S+$");

	private final InvoiceRepository invoices;

	private final CopyRequestRepository copyRequests;

	PortalController(InvoiceRepository invoices, CopyRequestRepository copyRequests) {
		this.invoices = invoices;
		this.copyRequests = copyRequests;
	}

	@GetMapping("/")
	String portal(@RequestAttribute(TenantFilter.SUPPLIER) Supplier supplier, Model model) {
		model.addAttribute("supplier", supplier);
		model.addAttribute("invoices", invoices.findBySupplierOrderByIssuedOnDesc(supplier));
		// Built from the request: the customer's domain, thanks to
		// CustomerDomainRequest, not your upstream host.
		model.addAttribute("portalUrl", ServletUriComponentsBuilder.fromCurrentContextPath().path("/").toUriString());
		return "portal";
	}

	// The supplier comes from the domain (TenantFilter), never from the form.
	@PostMapping("/copy-requests")
	String requestCopy(@RequestAttribute(TenantFilter.SUPPLIER) Supplier supplier,
			@RequestParam String invoiceNumber, @RequestParam String email, HttpServletRequest request,
			RedirectAttributes flash) {
		var invoice = invoices.findBySupplierAndNumber(supplier, invoiceNumber);
		if (invoice.isEmpty() || !EMAIL.matcher(email.trim()).matches()) {
			flash.addFlashAttribute("error", "Pick an invoice and enter a valid email.");
			return "redirect:/";
		}
		copyRequests.save(new CopyRequest(supplier, invoiceNumber, email.trim(), request.getRemoteAddr()));
		flash.addFlashAttribute("message", "Thanks. %s will email a copy of %s to %s."
			.formatted(supplier.getName(), invoiceNumber, email.trim()));
		// Relative, and server.tomcat.use-relative-redirects=true keeps it
		// that way, so the browser stays on the customer's domain.
		return "redirect:/";
	}

}
