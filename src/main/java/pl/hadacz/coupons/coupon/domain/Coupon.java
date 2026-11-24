package pl.hadacz.coupons.coupon.domain;

import java.time.Instant;
import java.util.Objects;

/// A discount coupon. Code, country, limit and validity period never change after creation;
/// only the usage counter does, and only through the atomic redemption operation in the repository.
///
/// The usage limit cannot be enforced on this object alone: several application instances
/// hold their own copies. The object states the rule; the database guarantees it.
public record Coupon(CouponCode code, CountryCode country, int maxUses, int currentUses,
                     ValidityPeriod validity, Instant createdAt) {

    public Coupon {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(validity, "validity");
        Objects.requireNonNull(createdAt, "createdAt");
        if (maxUses < 1) {
            throw new DomainValidationException("maxUses", "Maximum number of uses must be at least 1");
        }
        if (currentUses < 0 || currentUses > maxUses) {
            throw new IllegalArgumentException(
                    "Current uses %d out of range 0..%d".formatted(currentUses, maxUses));
        }
    }

    public static Coupon create(CouponCode code, CountryCode country, int maxUses, Instant createdAt) {
        return create(code, country, maxUses, ValidityPeriod.ALWAYS, createdAt);
    }

    /// A coupon that has already expired could never be redeemed, so creating one is a mistake.
    /// The check belongs here and not in the constructor: stored coupons do expire.
    public static Coupon create(CouponCode code, CountryCode country, int maxUses,
                                ValidityPeriod validity, Instant createdAt) {
        if (validity.hasEndedBy(createdAt)) {
            throw new DomainValidationException("validUntil", "Validity end must be in the future");
        }
        return new Coupon(code, country, maxUses, 0, validity, createdAt);
    }

    /// Safe to use as a fast-path check on a snapshot: the counter only grows,
    /// so a snapshot that says "exhausted" is never wrong.
    public boolean isExhausted() {
        return currentUses >= maxUses;
    }

    public boolean isAvailableIn(CountryCode clientCountry) {
        return country.equals(clientCountry);
    }
}
