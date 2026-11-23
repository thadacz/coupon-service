package pl.hadacz.coupons.coupon.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// An ISO 3166-1 alpha-2 country code, e.g. `PL`. Validated against the list the JDK ships.
public record CountryCode(String value) {

    private static final Set<String> ISO_COUNTRIES =
            Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

    public CountryCode {
        Objects.requireNonNull(value, "value");
        if (!ISO_COUNTRIES.contains(value)) {
            throw new DomainValidationException("country",
                    "Country must be an ISO 3166-1 alpha-2 code, e.g. PL");
        }
    }

    public static CountryCode of(@Nullable String raw) {
        if (raw == null) {
            throw new DomainValidationException("country", "Country is required");
        }
        return new CountryCode(raw.strip().toUpperCase(Locale.ROOT));
    }

    /// Lenient variant for data from external systems, where an unknown value is not a client error.
    public static Optional<CountryCode> tryParse(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        return ISO_COUNTRIES.contains(normalized) ? Optional.of(new CountryCode(normalized)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
