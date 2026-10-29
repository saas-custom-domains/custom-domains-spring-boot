package com.example.billport;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Events: https://docs.saascustomdomains.com/webhooks */
@RestController
class WebhookController {

	record Event(String event, Domain domain) {
		record Domain(String uuid, String host) {
		}
	}

	private final CustomDomainRepository domains;

	private final String webhookToken;

	WebhookController(CustomDomainRepository domains, AppProperties props) {
		this.domains = domains;
		this.webhookToken = props.webhookToken();
	}

	@PostMapping("/webhooks/saascustomdomains")
	@Transactional
	ResponseEntity<Void> receive(@RequestParam(defaultValue = "") String token, @RequestBody Event event) {
		if (!Tokens.matches(token, webhookToken)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		var found = event.domain() == null ? null : domains.findByProviderUuid(event.domain().uuid()).orElse(null);
		// Not ours (or already removed): acknowledge so it isn't retried.
		if (found == null) {
			return ResponseEntity.noContent().build();
		}
		switch (event.event()) {
			case "domain_dns_proxy_record_valid" -> found.setStatus(CustomDomain.Status.ACTIVE);
			case "domain_dns_proxy_record_invalid" -> found.setStatus(CustomDomain.Status.PENDING);
			case "tls_certificate_issued" -> found.setHttpsReady(true);
			case "domain_deleted" -> domains.delete(found);
			default -> {
			}
		}
		return ResponseEntity.noContent().build();
	}

}
