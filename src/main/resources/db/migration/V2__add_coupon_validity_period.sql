-- Optional validity period: a coupon can be redeemed from valid_from (inclusive) until
-- valid_until (exclusive). NULL means open on that side, so existing coupons stay valid.
ALTER TABLE coupons
    ADD COLUMN valid_from  TIMESTAMPTZ,
    ADD COLUMN valid_until TIMESTAMPTZ,
    ADD CONSTRAINT ck_coupons_validity_period
        CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_from < valid_until);
