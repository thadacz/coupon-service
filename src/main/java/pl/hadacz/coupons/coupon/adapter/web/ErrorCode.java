package pl.hadacz.coupons.coupon.adapter.web;

import java.net.URI;
import java.util.Locale;

/// Stable, machine-readable error codes. Clients branch on these, not on HTTP status alone:
/// two different problems can share a status (e.g. 409 for "limit reached" and "already redeemed").
enum ErrorCode {

    VALIDATION_FAILED("Request validation failed"),
    MALFORMED_REQUEST("Malformed request body"),
    COUPON_CODE_ALREADY_EXISTS("Coupon code already exists"),
    COUPON_NOT_FOUND("Coupon not found"),
    COUPON_COUNTRY_NOT_ALLOWED("Coupon not available in your country"),
    COUPON_NOT_YET_VALID("Coupon not valid yet"),
    COUPON_EXPIRED("Coupon expired"),
    COUPON_USAGE_LIMIT_REACHED("Coupon usage limit reached"),
    COUPON_ALREADY_REDEEMED("Coupon already redeemed by this user"),
    REDEMPTION_NOT_FOUND("Coupon not redeemed by this user"),
    SERVICE_UNAVAILABLE("Service temporarily unavailable"),
    UNSUPPORTED_REQUEST("Request not supported"),
    INTERNAL_ERROR("Internal error");

    private final String title;

    ErrorCode(String title) {
        this.title = title;
    }

    String title() {
        return title;
    }

    URI type() {
        return URI.create("urn:problem-type:" + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
