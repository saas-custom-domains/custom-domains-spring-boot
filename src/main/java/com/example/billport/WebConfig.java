package com.example.billport;

import java.lang.reflect.Method;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
class WebConfig {

	@Bean
	FilterRegistrationBean<TenantFilter> tenantFilter(Hosts hosts, SupplierRepository suppliers,
			AppProperties props) {
		var registration = new FilterRegistrationBean<>(
				new TenantFilter(hosts, suppliers, props.proxyAuthToken()));
		// Before Spring Security's filter chain (-100). A plain @Component
		// filter would run after it, and Spring Security would see your
		// upstream host instead of the customer's domain.
		registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
		return registration;
	}

	/** Routes @CustomerSite controllers by host, everything else to APP_HOSTS. */
	@Bean
	WebMvcRegistrations siteRouting() {
		return new WebMvcRegistrations() {
			@Override
			public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
				return new RequestMappingHandlerMapping() {
					// A method condition, not getCustomTypeCondition(): that one
					// is skipped for controllers without a class-level
					// @RequestMapping.
					@Override
					protected RequestCondition<?> getCustomMethodCondition(Method method) {
						return new SiteCondition(method.getDeclaringClass().isAnnotationPresent(CustomerSite.class));
					}
				};
			}
		};
	}

	/**
	 * Matches customer-site handlers only when TenantFilter found a supplier,
	 * and the rest only when it didn't. Error pages render on both.
	 */
	record SiteCondition(boolean customerSite) implements RequestCondition<SiteCondition> {

		@Override
		public SiteCondition combine(SiteCondition other) {
			return other;
		}

		@Override
		public SiteCondition getMatchingCondition(HttpServletRequest request) {
			if (request.getDispatcherType() == DispatcherType.ERROR) {
				return this;
			}
			boolean onCustomerDomain = request.getAttribute(TenantFilter.SUPPLIER) != null;
			return onCustomerDomain == customerSite ? this : null;
		}

		@Override
		public int compareTo(SiteCondition other, HttpServletRequest request) {
			return 0;
		}

	}

}
