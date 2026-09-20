-- API users for JWT login. Passwords are BCrypt hashes; plaintext never reaches the database.
CREATE TABLE app_users (
    id                  UUID            PRIMARY KEY,
    username            VARCHAR(64)     NOT NULL,
    password_hash       VARCHAR(72)     NOT NULL,
    -- The merchant a MERCHANT user acts for; NULL for staff such as reviewers.
    merchant_id         VARCHAR(64),
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- Also serves the login lookup.
    CONSTRAINT uq_app_users_username UNIQUE (username),
    -- Usernames are stored lower case; the login service lower-cases input before the lookup.
    CONSTRAINT ck_app_users_username CHECK (username ~ '^[a-z0-9._-]{3,64}$'),
    CONSTRAINT ck_app_users_password_hash_bcrypt CHECK (password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$')
);

CREATE TABLE user_roles (
    user_id             UUID            NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    role                VARCHAR(16)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- The primary key's leading user_id column serves loading a user's roles.
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('MERCHANT', 'REVIEWER'))
);
