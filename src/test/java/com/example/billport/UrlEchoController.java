package com.example.billport;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Test only: shows the base URL the app builds for the current request. */
@TestComponent
@RestController
class UrlEchoController {

	@GetMapping("/test/base-url")
	String baseUrl(HttpServletRequest request) {
		return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
	}

}
