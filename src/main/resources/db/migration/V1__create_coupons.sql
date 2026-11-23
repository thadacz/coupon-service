-- Coupons. current_uses is the concurrency control point: it is only ever changed by a single
-- conditional UPDATE, so the CHECK constraints below are a safety net, not the primary guard.
CREATE TABLE coupons
(
    id           UUID PRIMARY KEY     DEFAULT uuidv7(),
    code         VARCHAR(32) NOT NULL,
    country_code VARCHAR(2)  NOT NULL,
    max_uses     INTEGER     NOT NULL,
    current_uses INTEGER     NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_coupons_code UNIQUE (code),
    -- Codes are stored normalized (upper case), which makes a plain UNIQUE case-insensitive.
    CONSTRAINT ck_coupons_code_format CHECK (code ~ '^[A-Z0-9_-]{3,32}$'),
    CONSTRAINT ck_coupons_country_format CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_coupons_max_uses_positive CHECK (max_uses > 0),
    CONSTRAINT ck_coupons_uses_within_limit CHECK (current_uses BETWEEN 0 AND max_uses)
);

-- One row per successful redemption. The unique pair enforces "one user, one use".
-- The client IP is deliberately not stored (personal data, not needed afterwards).
CREATE TABLE coupon_redemptions
(
    id          UUID PRIMARY KEY     DEFAULT uuidv7(),
    coupon_id   UUID        NOT NULL REFERENCES coupons (id),
    user_id     VARCHAR(64) NOT NULL,
    redeemed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_coupon_redemptions_coupon_user UNIQUE (coupon_id, user_id)
);
