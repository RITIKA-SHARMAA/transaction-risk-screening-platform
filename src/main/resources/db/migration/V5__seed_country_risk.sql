-- Illustrative country risk scores for development and tests. Loosely modelled on the shape of public
-- AML country lists; NOT compliance data. Production values must come from the compliance team via a
-- new migration or an admin process. Countries not listed here are handled by the COUNTRY_RISK rule's
-- "defaultRiskScore" parameter.
INSERT INTO country_risk (country_code, country_name, risk_level, risk_score) VALUES
    ('US', 'United States',            'LOW',        10),
    ('CA', 'Canada',                   'LOW',        10),
    ('GB', 'United Kingdom',           'LOW',        10),
    ('DE', 'Germany',                  'LOW',        10),
    ('FR', 'France',                   'LOW',        10),
    ('NL', 'Netherlands',              'LOW',        10),
    ('IE', 'Ireland',                  'LOW',        12),
    ('SE', 'Sweden',                   'LOW',        8),
    ('CH', 'Switzerland',              'LOW',        15),
    ('JP', 'Japan',                    'LOW',        10),
    ('SG', 'Singapore',                'LOW',        15),
    ('AU', 'Australia',                'LOW',        10),
    ('IN', 'India',                    'MEDIUM',     35),
    ('BR', 'Brazil',                   'MEDIUM',     40),
    ('MX', 'Mexico',                   'MEDIUM',     45),
    ('AE', 'United Arab Emirates',     'MEDIUM',     45),
    ('TR', 'Turkey',                   'MEDIUM',     50),
    ('ZA', 'South Africa',             'MEDIUM',     50),
    ('NG', 'Nigeria',                  'HIGH',       70),
    ('VN', 'Vietnam',                  'HIGH',       70),
    ('SY', 'Syria',                    'HIGH',       85),
    ('MM', 'Myanmar',                  'HIGH',       90),
    ('IR', 'Iran',                     'PROHIBITED', 100),
    ('KP', 'North Korea',              'PROHIBITED', 100);
