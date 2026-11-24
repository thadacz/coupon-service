package pl.hadacz.coupons.coupon.domain;

import java.time.Instant;
import java.time.temporal.TemporalUnit;

import org.jspecify.annotations.Nullable;

/// When a coupon can be redeemed: from `validFrom` (inclusive) until `validUntil` (exclusive).
/// Either end may be open: a coupon without dates is valid from creation, forever.
///
/// Half-open on purpose: `validUntil = 2026-12-01T00:00Z` means "through the whole of November",
/// and two back-to-back campaigns never overlap by an instant.
public record ValidityPeriod(@Nullable Instant validFrom, @Nullable Instant validUntil) {

    public static final ValidityPeriod ALWAYS = new ValidityPeriod(null, null);

    public ValidityPeriod {
        if (validFrom != null && validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new DomainValidationException("validUntil", "Validity end must be after its start");
        }
    }

    public boolean hasNotStartedAt(Instant instant) {
        return validFrom != null && instant.isBefore(validFrom);
    }

    public boolean hasEndedBy(Instant instant) {
        return validUntil != null && !instant.isBefore(validUntil);
    }

    public ValidityPeriod truncatedTo(TemporalUnit unit) {
        return new ValidityPeriod(
                validFrom == null ? null : validFrom.truncatedTo(unit),
                validUntil == null ? null : validUntil.truncatedTo(unit));
    }
}
