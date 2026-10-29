MERGE INTO supplier (id, slug, name, tagline) KEY (id) VALUES
  (1, 'acme', 'Acme Supplies', 'Office and warehouse supplies, delivered weekly.'),
  (2, 'globex', 'Globex Freight', 'Pallet freight across Europe.');

MERGE INTO invoice (id, supplier_id, number, issued_on, amount, status) KEY (id) VALUES
  (1, 1, 'INV-1041', DATE '2026-08-31', 1240.00, 'Paid'),
  (2, 1, 'INV-1042', DATE '2026-09-30', 980.50, 'Paid'),
  (3, 1, 'INV-1043', DATE '2026-10-26', 1312.75, 'Open'),
  (4, 2, 'GF-2026-118', DATE '2026-09-15', 4800.00, 'Paid'),
  (5, 2, 'GF-2026-131', DATE '2026-10-15', 5120.00, 'Open');
