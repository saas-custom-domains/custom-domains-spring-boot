# Custom domains in Spring Boot

An example Spring Boot 4 app (Spring MVC, Thymeleaf, Spring Security, Spring Data JPA) where each customer serves its portal on its own domain. It's a small B2B invoicing product, Billport: every supplier gets a client portal where its clients see their invoices and request copies, and the supplier can put that portal on `billing.theircompany.com` instead of a Billport URL.

It goes with the guide [How to Add Custom Domains to a Spring Boot App](https://saascustomdomains.com/blog/posts/custom-domains-spring-boot).

What's in it:

- `TenantFilter`, a `OncePerRequestFilter` that runs before Spring Security, finds the supplier from the request's hostname. Unknown and unverified domains get a 404.
- Requests from the [SaaS Custom Domains](https://saascustomdomains.com) proxy are accepted only with the upstream's auth token, so nobody can claim a customer's domain by sending `X-Served-For` themselves.
- Once the token checks out, the request is wrapped so Spring sees the customer's domain: `ServletUriComponentsBuilder`, Spring Security and CORS all use it (see [Spring Boot gotchas](#spring-boot-gotchas)).
- A "request a copy of an invoice" form on each portal, with Spring Security's CSRF token, that works through the proxy.
- `@CustomerSite` controllers only answer on customer domains, everything else only on your own hostnames.
- A dashboard to add a domain, check its DNS and remove it. Webhooks update the status.
- [SaaS Custom Domains](https://saascustomdomains.com) issues and renews every customer's TLS certificate and proxies their traffic to the app.

## Run it in 2 minutes

You need Docker.

```bash
git clone https://github.com/saas-custom-domains/custom-domains-spring-boot
cd custom-domains-spring-boot
docker compose up --build
```

1. Open http://localhost:3000 and add `billing.acme.localhost` to Acme Supplies.
2. Within a few seconds the status changes to **Live**: the mock API "found" the DNS records and sent a webhook.
3. Open http://billing.acme.localhost. The request goes through the mock proxy on port 80, exactly like a real customer domain goes through SaaS Custom Domains, and you see Acme's client portal. Try the "Request a copy" form.

`*.localhost` always resolves to your own machine in Chrome, Firefox, Safari and curl, so there's nothing to add to `/etc/hosts`.

The mock is [scd-mock](https://github.com/saas-custom-domains/scd-mock): a local copy of the SaaS Custom Domains API and proxy, so you can try everything without an account. Its proxy speaks plain HTTP, so `compose.yaml` sets `SCD_MOCK_FORWARDED_PROTO=http`; production always sends `X-Forwarded-Proto: https`.

### Run the app outside Docker

You need Java 25. Keep the mock in Docker and point it at your machine:

```bash
SCD_MOCK_UPSTREAM_HOST=host.docker.internal \
SCD_MOCK_WEBHOOK_URL=http://host.docker.internal:3000/webhooks/saascustomdomains \
  docker compose up scd-mock -d
cp .env.example .env
set -a; . ./.env; set +a
./mvnw spring-boot:run
```

## How it works

```
Browser ── billing.acme.com ──▶ SaaS Custom Domains ──▶ your Spring Boot app
                                (TLS certificate,       Host: app.billport.example
                                 proxy)                 X-Served-For: billing.acme.com
                                                        X-SaaS-Domains-Auth-Token: …
```

The proxy sends your app's own hostname in `Host` and `X-Forwarded-Host`, because that's what hosting platforms route on and what many apps expect (allowed hosts, OAuth callbacks, links to the app itself). The customer's domain arrives separately in `X-Served-For`.

| File | What it does |
| --- | --- |
| `TenantFilter.java` | Checks the proxy token, picks the host, finds the supplier, wraps the request |
| `CustomerDomainRequest.java` | The request as the browser sent it: customer's domain, `https`, visitor's IP |
| `WebConfig.java` | Registers the filter before Spring Security; routes `@CustomerSite` controllers by host |
| `PortalController.java` | The supplier's client portal and its form |
| `DashboardController.java` | Add, check and remove domains |
| `DomainService.java` | Our row first, then the API; removes the row if the API refuses |
| `SaasDomainsClient.java` | API client for SaaS Custom Domains, with Spring's `RestClient` |
| `WebhookController.java` | Webhook endpoint |
| `schema.sql` | Tables, with a unique index on the custom domain's host |

## Spring Boot gotchas

Each one has a test, so the weekly CI run notices if a Spring release changes the behavior.

### A `@Component` filter runs after Spring Security

Spring Boot registers a `Filter` bean at the lowest precedence, which puts it after Spring Security's filter chain (order -100). Anything in that chain that looks at the host or the scheme (CORS, the HSTS header, saved-request URLs) then sees your upstream host. `WebConfig` registers `TenantFilter` with a `FilterRegistrationBean` at `SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10`, and the filter is not a `@Component`, so it isn't registered twice. Tests: `tenantFilterRunsBeforeSpringSecurity` (Spring Security's HSTS header only appears when it saw the wrapped HTTPS request), `corsStillFailsIfTheTenantFilterRunsAfter`.

### Redirects go to your upstream host: `server.tomcat.use-relative-redirects`

After the portal form, `return "redirect:/"` should keep the browser on `billing.acme.com`. Spring Boot sets Tomcat's `useRelativeRedirects` to `false`, so Tomcat turns `/` into an absolute URL built from the request it received:

```
Location: http://app:3000/
```

With `server.forward-headers-strategy=native` it's `https://app.billport.example/` instead, from `X-Forwarded-Host`. Either way the customer lands on your hostname. Wrapping the request doesn't help here: Tomcat uses its own request object, not the wrapper. `application.properties` sets `server.tomcat.use-relative-redirects=true`, and the browser resolves `Location: /` against the customer's domain. (With `server.forward-headers-strategy=framework`, the same property makes Spring's `ForwardedHeaderFilter` answer `303 See Other` with a relative `Location`.) Tests: `RedirectGotchaTest`, the end-to-end test.

### `server.forward-headers-strategy` doesn't know about `X-Served-For`

- `native` hands `X-Forwarded-For`, `X-Forwarded-Proto` and `X-Forwarded-Host` to Tomcat's `RemoteIpValve`, which trusts them only from addresses in `server.tomcat.remoteip.internal-proxies` (private ranges by default). From anywhere else it ignores them.
- `framework` uses Spring's `ForwardedHeaderFilter`, which trusts them from anyone.
- The default is `none`, unless Spring Boot detects a cloud platform (Kubernetes, Heroku, Cloud Foundry, Azure App Service, AWS ECS and a few more), where it acts like `native`.

Both give you `X-Forwarded-Host`, which is your own hostname. Neither reads `X-Served-For`. So `TenantFilter` doesn't depend on this setting: it reads the `Host` header (with `framework`, `getServerName()` is whatever `X-Forwarded-Host` a visitor sent), and for proxied requests it uses `X-Served-For` after the token check. Keep whatever strategy your platform needs for your own traffic. Tests: `ForwardHeadersGotchaTest`, `ignoresXForwardedHostForTenantLookup`.

### Absolute URLs: `ServletUriComponentsBuilder`

Since Spring Framework 5.1, `ServletUriComponentsBuilder` doesn't read `X-Forwarded-*` itself; it uses the request's scheme, server name and port. Behind the proxy that's `http://app:3000`, so a link you print in an email or a page (`Portal address: …` on the portal) points at your app. `CustomerDomainRequest` overrides `getServerName()`, `getScheme()`, `getServerPort()`, `isSecure()`, `getRequestURL()` and the `Host` header, so the same code prints `https://billing.acme.com/`. It also returns the visitor's IP from `X-SaaS-Domains-IP` in `getRemoteAddr()`. Tests: `readsXServedForWithTheProxyToken`, `copyRequestThroughTheProxyUsesTheDomainsSupplier`, the end-to-end test.

### CSRF: Spring Security checks the token, not the origin

Spring Security 7.1 keeps the CSRF token in the HTTP session and compares it with the `_csrf` field that `th:action` adds to every form. It doesn't compare `Origin` or `Host`, so forms work on customer domains as long as the session cookie comes back. Two ways it doesn't:

- The cookie is marked `Secure` and the page is plain HTTP. Tomcat sets `Secure` when its own request is secure, which with `native` means "`X-Forwarded-Proto: https` from a private address". Through the mock's plain-HTTP proxy, the browser then drops `JSESSIONID` and every form fails with a bare 403. With `logging.level.org.springframework.security.web.csrf=DEBUG` the log says `Invalid CSRF token found for https://billing.acme.localhost/copy-requests`. In production everything is HTTPS and this doesn't happen; locally, the mock's `SCD_MOCK_FORWARDED_PROTO=http` avoids it.
- You set `server.servlet.session.cookie.domain` to your own domain. Browsers reject a cookie for `billport.example` sent from `billing.acme.com`. Leave it unset: the cookie is then host-only, and each customer domain gets its own session (someone signed in on your dashboard is not signed in on a customer's portal, which is what you want).

Tests: `checksTheTokenNotTheOrigin`, `rejectsAFormWithoutTheToken`, `RedirectGotchaTest.WithNativeForwardedHeaders` (the `Secure` flag).

### CORS: `403 Invalid CORS request` on a same-origin form

If you configure CORS for paths the portal posts to (a `/**` mapping for your SPA is common), Spring compares the browser's `Origin` with the request's scheme, host and port. A form on `https://billing.acme.com` posting to its own page looks cross-origin, because the request says `app.billport.example`, and the answer is a 403 with the body `Invalid CORS request`. Adding customer domains to `allowedOrigins` doesn't scale. With `TenantFilter` in front of Spring Security, the request already says `billing.acme.com` and it isn't a CORS request at all. Tests: `corsTreatsTheSameOriginFormAsCrossOrigin`, `corsPassesOnceTheTenantFilterRunsFirst`.

### Routing by host: `Ambiguous mapping`

Spring MVC has no host condition in `@RequestMapping`, and both the dashboard and the portal live at `/`. `WebConfig` adds a custom `RequestCondition`: `@CustomerSite` controllers match only when `TenantFilter` found a supplier, all other controllers only when it didn't. Override `getCustomMethodCondition`, not `getCustomTypeCondition`: the type condition is only used for controllers with a class-level `@RequestMapping`, so without one the app fails at startup with

```
Ambiguous mapping. Cannot map 'portalController' method
com.example.billport.PortalController#portal(Supplier, Model)
to {GET [/]}: There is already 'dashboardController' bean method
com.example.billport.DashboardController#dashboard(Model) mapped.
```

Tests: `portalRoutesAreHiddenOnTheAppHost`, `dashboardRoutesAreHiddenOnCustomerDomains`.

### Health checks and webhooks don't arrive on your domain

Load balancers and Docker call the health check by IP address, and the mock's webhooks arrive as `Host: app:3000`. Both look like unknown customer domains to a host check. `TenantFilter` skips `/actuator/health`, and `APP_HOSTS` lists every internal name (`app` in `compose.yaml`). Tests: `healthCheckSkipsTheHostCheck`, `WebhookTest`.

### The supplier comes from the request, not the form

Handlers take `@RequestAttribute(TenantFilter.SUPPLIER) Supplier supplier`. The portal form never says which supplier it's for, and an invoice number from another supplier is rejected. Test: `anotherSuppliersInvoiceIsRejected`.

### Option: Hibernate multitenancy

This example filters by supplier in its repository methods. If you'd rather have Hibernate do it, put `@TenantId` on a `supplierId` field of each tenant-owned entity (or use a schema per tenant with a `MultiTenantConnectionProvider`) and give Hibernate a resolver that reads the supplier `TenantFilter` stored:

```java
@Component
class SupplierTenantResolver
		implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

	@Override
	public Long resolveCurrentTenantIdentifier() {
		RequestAttributes request = RequestContextHolder.getRequestAttributes();
		Object supplier = request == null ? null
				: request.getAttribute(TenantFilter.SUPPLIER, RequestAttributes.SCOPE_REQUEST);
		// 0 = no supplier: your dashboard, webhooks, background jobs.
		return supplier instanceof Supplier s ? s.getId() : 0L;
	}

	@Override
	public boolean isRoot(Long tenantId) {
		return tenantId == 0L;
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return false;
	}

	@Override
	public void customize(Map<String, Object> properties) {
		properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
	}

}
```

`isRoot` lets your own code (the dashboard, webhooks) see every supplier's rows. This compiles against Hibernate 7.4; the example doesn't wire it in.

## Use it with your SaaS Custom Domains account

1. [Create an API token](https://app.saascustomdomains.com/api_tokens) and an upstream pointing at your deployed app.
2. Set the environment variables from `.env.example`: `SCD_API_TOKEN`, `SCD_ACCOUNT_UUID`, `SCD_UPSTREAM_UUID`, `PROXY_AUTH_TOKEN` (the upstream's auth token), `SCD_WEBHOOK_TOKEN` (from the webhook you create in the dashboard), and `APP_HOSTS`.
3. Remove `SCD_API_URL` so the client talks to the real API.

`APP_HOSTS` must list every hostname your own traffic arrives on: your app's domain, plus any internal name your platform uses to reach the app. Any other host is treated as a customer domain and gets a 404 if it isn't connected.

Your customers point a CNAME at `in.saascustomdomains.com` (or A records at our two static IPs for a root domain). When you add a domain with a DNS contact, we email them the exact records. See the [docs](https://docs.saascustomdomains.com).

(SaaS Custom Domains support can also put the customer's domain in `X-Forwarded-Host` for your upstream.)

## Tests

```bash
./mvnw test                    # JUnit: routing, tokens, webhooks, API client, Spring gotchas
docker compose up -d --build --wait
cd e2e
npm ci
npx playwright install chromium
npm test                       # end to end, through the mock proxy
```

Without Java installed, run the unit tests in Docker:

```bash
docker run --rm -v "$PWD":/src -w /src eclipse-temurin:25-jdk ./mvnw test
```

The end-to-end tests use `*.test` domains and send the browser's requests for them to the mock proxy, so nothing depends on DNS or on port 80. To run a second stack side by side, set `APP_PORT`, `SCD_MOCK_API_PORT` and `SCD_MOCK_PROXY_PORT` for both `docker compose` and `npm test`.

CI runs both on every push and once a week, so a new Spring Boot release that breaks something shows up here first.

## Tested with

| | Version |
| --- | --- |
| Spring Boot | 4.1.2 |
| Spring Framework | 7.0.10 |
| Spring Security | 7.1.2 |
| Hibernate ORM | 7.4.13 |
| Tomcat | 11.0.27 |
| Thymeleaf | 3.1.5 |
| H2 | 2.4.240 |
| Java (Eclipse Temurin) | 25.0.4 |
| Playwright | 1.64.1 |

Last checked: 2026-10-29.

## Not production-ready on purpose

There's no login. In your app, check in every dashboard handler that the signed-in user owns the supplier and the domain, and put the portal's invoices behind your clients' login. H2 in a file keeps the example small; `schema.sql` is plain SQL, so use your own database. Cache the host lookup in `TenantFilter` if it shows up in your profiles, and evict it when a domain changes.

## License

MIT
