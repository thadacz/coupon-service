package pl.hadacz.coupons.coupon.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import pl.hadacz.coupons.coupon.application.CouponQueries;
import pl.hadacz.coupons.coupon.application.RedeemCouponCommand;
import pl.hadacz.coupons.coupon.application.RedeemCouponHandler;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.IpAddress;
import pl.hadacz.coupons.coupon.domain.RedemptionResult;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.AlreadyRedeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CountryNotAllowed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CouponNotFound;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Expired;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.NotYetValid;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Redeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.UsageLimitReached;
import pl.hadacz.coupons.coupon.domain.UserId;

@RestController
@RequestMapping(path = "/api/v1/coupons/{code}/redemptions", produces = MediaType.APPLICATION_JSON_VALUE)
class RedemptionController {

    private final RedeemCouponHandler redeemCoupon;
    private final CouponQueries queries;
    private final RedemptionMetrics metrics;

    RedemptionController(RedeemCouponHandler redeemCoupon, CouponQueries queries, RedemptionMetrics metrics) {
        this.redeemCoupon = redeemCoupon;
        this.queries = queries;
        this.metrics = metrics;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> redeem(@PathVariable String code,
                                  @Valid @RequestBody RedeemCouponRequest request,
                                  HttpServletRequest http) {
        var command = new RedeemCouponCommand(
                CouponCode.of(code), UserId.of(request.userId()), IpAddress.of(http.getRemoteAddr()));

        RedemptionResult result = redeemCoupon.handle(command);
        metrics.record(result);
        return toResponse(result);
    }

    /// The outcome of an earlier redemption, for a client that did not get the answer to its POST.
    /// Two different 404s: the coupon does not exist, or this user has not redeemed it.
    @GetMapping("/{userId}")
    ResponseEntity<Object> get(@PathVariable String code, @PathVariable String userId) {
        CouponCode couponCode = CouponCode.of(code);
        UserId user = UserId.of(userId);
        return queries.findRedemption(couponCode, user)
                .<ResponseEntity<Object>>map(redemption -> ResponseEntity.ok(RedemptionResponse.from(redemption)))
                .orElseGet(() -> queries.findByCode(couponCode).isPresent()
                        ? Problems.response(HttpStatus.NOT_FOUND, ErrorCode.REDEMPTION_NOT_FOUND,
                                "User '%s' has not redeemed coupon '%s'.".formatted(user, couponCode))
                        : Problems.response(HttpStatus.NOT_FOUND, ErrorCode.COUPON_NOT_FOUND,
                                "Coupon '%s' does not exist.".formatted(couponCode)));
    }

    /// Exhaustive: adding a RedemptionResult variant breaks compilation here until it is mapped.
    private static ResponseEntity<Object> toResponse(RedemptionResult result) {
        return switch (result) {
            case Redeemed redeemed -> ResponseEntity
                    .created(ServletUriComponentsBuilder.fromCurrentContextPath()
                            .path("/api/v1/coupons/{code}/redemptions/{userId}")
                            .buildAndExpand(redeemed.code().value(), redeemed.userId().value()).toUri())
                    .body(RedemptionResponse.from(redeemed));
            case CouponNotFound(var code) -> Problems.response(HttpStatus.NOT_FOUND,
                    ErrorCode.COUPON_NOT_FOUND, "Coupon '%s' does not exist.".formatted(code));
            case NotYetValid(var code, var validFrom) -> Problems.response(HttpStatus.CONFLICT,
                    ErrorCode.COUPON_NOT_YET_VALID,
                    "Coupon '%s' can be redeemed from %s.".formatted(code, validFrom));
            case Expired(var code, var validUntil) -> Problems.response(HttpStatus.CONFLICT,
                    ErrorCode.COUPON_EXPIRED, "Coupon '%s' expired at %s.".formatted(code, validUntil));
            case UsageLimitReached(var code, var maxUses) -> Problems.response(HttpStatus.CONFLICT,
                    ErrorCode.COUPON_USAGE_LIMIT_REACHED,
                    "Coupon '%s' has reached its usage limit of %d.".formatted(code, maxUses));
            case AlreadyRedeemed(var code, var userId) -> Problems.response(HttpStatus.CONFLICT,
                    ErrorCode.COUPON_ALREADY_REDEEMED,
                    "User '%s' has already redeemed coupon '%s'.".formatted(userId, code));
            case CountryNotAllowed(var code, var required, var detected) -> Problems.response(HttpStatus.FORBIDDEN,
                    ErrorCode.COUPON_COUNTRY_NOT_ALLOWED, detected == null
                            ? "Coupon '%s' is only for %s and your country could not be determined.".formatted(code, required)
                            : "Coupon '%s' is only for %s, your request comes from %s.".formatted(code, required, detected));
        };
    }
}
