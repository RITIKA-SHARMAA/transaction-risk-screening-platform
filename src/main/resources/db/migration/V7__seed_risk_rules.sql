-- Initial rule set. The risk worker sums the weights of triggered RISK rules into a score; the screening
-- worker flags a hit when a WATCHLIST_MATCH rule's threshold is reached; the decision engine compares the
-- score with the enabled DECISION_CUTOFF rows (highest threshold reached wins, otherwise APPROVE).
-- Tune by adding a migration that updates these rows, never by changing Java code.
INSERT INTO risk_rules (code, name, description, rule_type, signal_type, decision, weight, threshold, params) VALUES
    -- Amount thresholds are per currency; no FX conversion is applied.
    ('AMOUNT_HIGH_USD', 'High amount (USD)', 'Transaction amount at or above threshold, USD only',
        'AMOUNT_THRESHOLD', 'RISK', NULL, 20.00, 5000.0000, '{"currency": "USD"}'),
    ('AMOUNT_VERY_HIGH_USD', 'Very high amount (USD)', 'Transaction amount at or above threshold, USD only',
        'AMOUNT_THRESHOLD', 'RISK', NULL, 35.00, 25000.0000, '{"currency": "USD"}'),
    ('AMOUNT_HIGH_EUR', 'High amount (EUR)', 'Transaction amount at or above threshold, EUR only',
        'AMOUNT_THRESHOLD', 'RISK', NULL, 20.00, 4500.0000, '{"currency": "EUR"}'),
    ('AMOUNT_VERY_HIGH_EUR', 'Very high amount (EUR)', 'Transaction amount at or above threshold, EUR only',
        'AMOUNT_THRESHOLD', 'RISK', NULL, 35.00, 22500.0000, '{"currency": "EUR"}'),

    -- Velocity thresholds are transaction counts per payer account inside the window, including this one.
    ('VELOCITY_1H', 'Payer velocity, 1 hour', 'Payer account transaction count within 60 minutes',
        'VELOCITY', 'RISK', NULL, 25.00, 5, '{"windowMinutes": 60}'),
    ('VELOCITY_24H', 'Payer velocity, 24 hours', 'Payer account transaction count within 24 hours',
        'VELOCITY', 'RISK', NULL, 15.00, 20, '{"windowMinutes": 1440}'),

    -- Country threshold is compared with country_risk.risk_score for either party.
    ('COUNTRY_HIGH_RISK', 'High-risk country', 'Payer or payee country risk score at or above threshold',
        'COUNTRY_RISK', 'RISK', NULL, 30.00, 70, '{"parties": ["PAYER", "PAYEE"], "defaultRiskScore": 50}'),
    ('COUNTRY_PROHIBITED', 'Prohibited country', 'Payer or payee country risk score at or above threshold',
        'COUNTRY_RISK', 'RISK', NULL, 100.00, 100, '{"parties": ["PAYER", "PAYEE"], "defaultRiskScore": 50}'),
    ('CROSS_BORDER', 'Cross-border payment', 'Payer and payee countries differ',
        'CROSS_BORDER', 'RISK', NULL, 10.00, NULL, '{}'),

    -- Screening: threshold is the minimum name match score (0..1) that counts as a hit.
    ('WATCHLIST_NAME_MATCH', 'Watchlist name match', 'Payer or payee name matches an active watchlist entry',
        'WATCHLIST_MATCH', 'SCREENING', NULL, 100.00, 0.9000, '{"fields": ["PAYER_NAME", "PAYEE_NAME"]}'),

    -- Decision cut-offs on the risk score. A screening hit is handled by the decision engine's own rules.
    ('DECISION_REVIEW', 'Manual review cut-off', 'Risk score at or above threshold goes to manual review',
        'DECISION_CUTOFF', NULL, 'REVIEW', 0, 40, '{}'),
    ('DECISION_BLOCK', 'Block cut-off', 'Risk score at or above threshold is blocked',
        'DECISION_CUTOFF', NULL, 'BLOCK', 0, 75, '{}');

-- Shipped disabled: kept for tuning experiments, ignored by workers until enabled.
INSERT INTO risk_rules (code, name, description, rule_type, signal_type, decision, weight, threshold, params, enabled) VALUES
    ('VELOCITY_10M', 'Payer velocity, 10 minutes', 'Payer account transaction count within 10 minutes',
        'VELOCITY', 'RISK', NULL, 20.00, 3, '{"windowMinutes": 10}', FALSE);
