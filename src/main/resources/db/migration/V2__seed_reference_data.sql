-- Default pricing multipliers (admin can change via API)
INSERT INTO pricing_tiers (code, description, multiplier) VALUES
    ('SEAT_REGULAR', 'Regular seat price multiplier', 1.00),
    ('SEAT_PREMIUM', 'Premium seat price multiplier', 1.50),
    ('WEEKEND', 'Weekend (Sat/Sun) surcharge multiplier', 1.25);

-- Default refund policy: >=24h before show 100%, >=2h 50%, otherwise 0%
INSERT INTO refund_rules (min_hours_before_show, refund_percent) VALUES
    (24, 100),
    (2, 50),
    (0, 0);
