package pl.hadacz.coupons.coupon.domain;

import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// Identifier of the user redeeming a coupon. The task allows any identifier, so the rules
/// only keep it bounded and printable. In production it would come from the access token.
public record UserId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9._:@-]{1,64}");

    public UserId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new DomainValidationException("userId",
                    "User id must have 1 to 64 characters: letters, digits or . _ : @ -");
        }
    }

    public static UserId of(@Nullable String raw) {
        if (raw == null) {
            throw new DomainValidationException("userId", "User id is required");
        }
        return new UserId(raw.strip());
    }

    @Override
    public String toString() {
        return value;
    }
}
