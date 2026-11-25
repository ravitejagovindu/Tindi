CREATE TABLE users (
    id            CHAR(36)     NOT NULL PRIMARY KEY,
    name          VARCHAR(60)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_tokens (
    id           CHAR(36)    NOT NULL PRIMARY KEY,
    user_id      CHAR(36)    NOT NULL,
    token_hash   CHAR(64)    NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_auth_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE categories (
    id         CHAR(36)    NOT NULL PRIMARY KEY,
    user_id    CHAR(36)    NULL,
    name       VARCHAR(30) NOT NULL,
    starter    BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order INT         NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_categories_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recipes (
    id          CHAR(36)     NOT NULL PRIMARY KEY,
    user_id     CHAR(36)     NOT NULL,
    name        VARCHAR(120) NOT NULL,
    ingredients JSON         NOT NULL,
    steps       JSON         NOT NULL,
    source_type ENUM('YOUTUBE','PASTED') NOT NULL,
    source_url  VARCHAR(2048) NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_recipes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_recipes_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recipe_categories (
    recipe_id   CHAR(36) NOT NULL,
    category_id CHAR(36) NOT NULL,
    PRIMARY KEY (recipe_id, category_id),
    CONSTRAINT fk_rc_recipe   FOREIGN KEY (recipe_id)   REFERENCES recipes (id)    ON DELETE CASCADE,
    CONSTRAINT fk_rc_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO categories (id, user_id, name, starter, sort_order, created_at) VALUES
 ('00000000-0000-4000-8000-000000000001', NULL, 'Breakfast', TRUE, 1, UTC_TIMESTAMP(6)),
 ('00000000-0000-4000-8000-000000000002', NULL, 'Lunch',     TRUE, 2, UTC_TIMESTAMP(6)),
 ('00000000-0000-4000-8000-000000000003', NULL, 'Snacks',    TRUE, 3, UTC_TIMESTAMP(6)),
 ('00000000-0000-4000-8000-000000000004', NULL, 'Dinner',    TRUE, 4, UTC_TIMESTAMP(6)),
 ('00000000-0000-4000-8000-000000000005', NULL, 'Healthy',   TRUE, 5, UTC_TIMESTAMP(6)),
 ('00000000-0000-4000-8000-000000000006', NULL, 'Protein',   TRUE, 6, UTC_TIMESTAMP(6));
