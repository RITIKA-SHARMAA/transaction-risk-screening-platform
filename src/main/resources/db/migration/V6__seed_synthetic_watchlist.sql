-- Synthetic watchlist for development and tests. Every name is invented and every entry is tagged
-- list_source = 'SYNTHETIC'; real sanctions lists are loaded separately and never committed here.
-- normalized_name uses the same rule as NameNormalizer: lower case, runs of non-alphanumerics collapsed
-- to one space, trimmed (seed names are ASCII, so no accent folding is needed in SQL).
INSERT INTO watchlist_entries (list_source, source_reference, entity_type, full_name, normalized_name, country, reason, active)
SELECT 'SYNTHETIC', ref, entity_type, full_name,
       lower(btrim(regexp_replace(full_name, '[^A-Za-z0-9]+', ' ', 'g'))),
       country, reason, active
FROM (VALUES
    ('SYN-0001', 'INDIVIDUAL',   'Quentin Marlow-Vance',            'GB', 'Synthetic: sanctions evasion',        TRUE),
    ('SYN-0002', 'INDIVIDUAL',   'Elena V. Draskovic',              'RS', 'Synthetic: fraud ring organiser',     TRUE),
    ('SYN-0003', 'INDIVIDUAL',   'Tobias Renquist',                 'SE', 'Synthetic: money laundering',         TRUE),
    ('SYN-0004', 'INDIVIDUAL',   'Amara Okonkwo-Blythe',            'NG', 'Synthetic: mule account controller',  TRUE),
    ('SYN-0005', 'INDIVIDUAL',   'Hiroshi Tanabe-Kerr',             'JP', 'Synthetic: payment fraud',            TRUE),
    ('SYN-0006', 'INDIVIDUAL',   'Lucia Ferreira Montalban',        'BR', 'Synthetic: identity fraud',           TRUE),
    ('SYN-0007', 'INDIVIDUAL',   'Dmitri Olenev',                   NULL, 'Synthetic: terrorist financing',      TRUE),
    ('SYN-0008', 'INDIVIDUAL',   'Priya Castellano',                'IN', 'Synthetic: delisted entry',           FALSE),
    ('SYN-0101', 'ORGANIZATION', 'Northwind Shell Holdings Ltd.',   'AE', 'Synthetic: front company',            TRUE),
    ('SYN-0102', 'ORGANIZATION', 'Bluefin Trade & Logistics LLC',   'MX', 'Synthetic: trade-based laundering',   TRUE),
    ('SYN-0103', 'ORGANIZATION', 'Orion Crest Capital S.A.',        'CH', 'Synthetic: sanctioned investment',    TRUE),
    ('SYN-0104', 'ORGANIZATION', 'Kestrel Imports (Pvt) Limited',   'IN', 'Synthetic: dual-use goods broker',    TRUE),
    ('SYN-0105', 'ORGANIZATION', 'Grey Harbor Maritime Co.',        'MM', 'Synthetic: embargo shipping',         TRUE)
) AS seed (ref, entity_type, full_name, country, reason, active);
