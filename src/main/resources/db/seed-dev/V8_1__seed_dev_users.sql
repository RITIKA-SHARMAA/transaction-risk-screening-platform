-- DEVELOPMENT AND TEST ONLY. This location (classpath:db/seed-dev) is added to spring.flyway.locations by
-- the local and test profiles only; the base configuration never loads it, so these well-known
-- credentials cannot be seeded into another environment.
--
--   merchant.demo / merchant-dev-password   MERCHANT, merchant_id = merchant-demo
--   reviewer.demo / reviewer-dev-password   REVIEWER
--
-- Versioned 8.1 so it slots after V8 without taking a version number the main migrations will need.
INSERT INTO app_users (id, username, password_hash, merchant_id) VALUES
    ('7b0f1e1c-3f5a-4a52-9d0e-6f6b8a1d2c01', 'merchant.demo',
     '$2a$12$Jq/kqAVO/rNCeQeEBTdkweBLvy.xFy0yMjjDdOC0I20XimTWwdPn6', 'merchant-demo'),
    ('7b0f1e1c-3f5a-4a52-9d0e-6f6b8a1d2c02', 'reviewer.demo',
     '$2a$12$18TQ2GkribgO4hkTEthSvOLgFlHfxYCtk8cOz8NnIszcYxwV76EH6', NULL);

INSERT INTO user_roles (user_id, role) VALUES
    ('7b0f1e1c-3f5a-4a52-9d0e-6f6b8a1d2c01', 'MERCHANT'),
    ('7b0f1e1c-3f5a-4a52-9d0e-6f6b8a1d2c02', 'REVIEWER');
