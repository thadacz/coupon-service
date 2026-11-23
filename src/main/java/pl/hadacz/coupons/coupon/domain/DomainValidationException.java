package pl.hadacz.coupons.coupon.domain;

import java.io.Serial;

/// Input that breaks a domain rule, e.g. a malformed coupon code. Mapped to HTTP 400.
public final class DomainValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public DomainValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
