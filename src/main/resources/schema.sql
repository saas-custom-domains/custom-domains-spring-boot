CREATE TABLE IF NOT EXISTS supplier (
  id BIGINT PRIMARY KEY,
  slug VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  tagline VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS invoice (
  id BIGINT PRIMARY KEY,
  supplier_id BIGINT NOT NULL REFERENCES supplier (id),
  number VARCHAR(20) NOT NULL,
  issued_on DATE NOT NULL,
  amount DECIMAL(12, 2) NOT NULL,
  status VARCHAR(10) NOT NULL,
  UNIQUE (supplier_id, number)
);

CREATE TABLE IF NOT EXISTS custom_domain (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_id BIGINT NOT NULL REFERENCES supplier (id) ON DELETE CASCADE,
  host VARCHAR(253) NOT NULL,
  status VARCHAR(10) DEFAULT 'PENDING' NOT NULL,
  https_ready BOOLEAN DEFAULT FALSE NOT NULL,
  provider_uuid VARCHAR(64),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
-- One supplier per host. Every customer-facing request looks this up.
CREATE UNIQUE INDEX IF NOT EXISTS custom_domain_host ON custom_domain (host);
CREATE UNIQUE INDEX IF NOT EXISTS custom_domain_provider_uuid ON custom_domain (provider_uuid);

CREATE TABLE IF NOT EXISTS copy_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_id BIGINT NOT NULL REFERENCES supplier (id) ON DELETE CASCADE,
  invoice_number VARCHAR(20) NOT NULL,
  email VARCHAR(254) NOT NULL,
  client_ip VARCHAR(45),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
