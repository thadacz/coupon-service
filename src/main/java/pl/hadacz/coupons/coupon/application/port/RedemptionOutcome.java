package pl.hadacz.coupons.coupon.application.port;

/// Result of the atomic "record the redemption and consume one use" operation.
public enum RedemptionOutcome {
    REDEEMED,
    ALREADY_REDEEMED,
    LIMIT_REACHED
}
