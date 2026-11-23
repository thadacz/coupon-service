package pl.hadacz.coupons.coupon.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// A coupon code. Codes are case-insensitive, so every code is normalized to upper case
/// once, here: `WIOSNA`, `wiosna` and ` Wiosna ` are the same code.
///
/// `Locale.ROOT` is deliberate: under a Turkish default locale `"i".toUpperCase()` returns `"İ"`,
/// and the same input would normalize differently on differently configured servers.
public record CouponCode(String value) {

    private static final Pattern RAW_FORMAT = Pattern.compile("[A-Za-z0-9_-]{3,32}");
    private static final Pattern NORMALIZED_FORMAT = Pattern.compile("[A-Z0-9_-]{3,32}");

    public CouponCode {
        Objects.requireNonNull(value, "value");
        if (!NORMALIZED_FORMAT.matcher(value).matches()) {
            throw invalid();
        }
    }

    /// Parses user input. The character set is checked before upper-casing, so that letters
    /// such as `ß` cannot turn into valid ASCII (`SS`) on the way.
    public static CouponCode of(@Nullable String raw) {
        if (raw == null || !RAW_FORMAT.matcher(raw.strip()).matches()) {
            throw invalid();
        }
        return new CouponCode(raw.strip().toUpperCase(Locale.ROOT));
    }

    private static DomainValidationException invalid() {
        return new DomainValidationException("code",
                "Coupon code must have 3 to 32 characters: letters A-Z, digits, '-' or '_'");
    }

    @Override
    public String toString() {
        return value;
    }
}
