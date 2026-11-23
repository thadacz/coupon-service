package pl.hadacz.coupons.coupon.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.hadacz.coupons.coupon.application.RedeemCouponCommand;
import pl.hadacz.coupons.coupon.application.RedeemCouponHandler;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.IpAddress;
import pl.hadacz.coupons.coupon.domain.RedemptionResult;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.AlreadyRedeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CountryNotAllowed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CouponNotFound;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.GeolocationUnavailable;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Redeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.UsageLimitReached;
import pl.hadacz.coupons.coupon.domain.UserId;

@RestController
@RequestMapping(path = "/api/v1/coupons/{code}/redemptions", produces = MediaType.APPLICATION_JSON_VALUE)
class RedemptionController {

    private static final String RETRY_AFTER_SECONDS = "5";

    private final RedeemCouponHandler redeemCoupon;

    RedemptionController(RedeemCouponHandler redeemCoupon) {
        this.redeemCoupon = redeemCoupon;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> redeem(@PathVariable String code,
                                  @Valid @RequestBody RedeemCouponRequest request,
                                  HttpServletRequest http) {
        var command = new RedeemCouponCommand(
                CouponCode.of(code), UserId.of(request.userId()), IpAddress.of(http.getRemoteAddr()));

        return toResponse(redeemCoupon.handle(command));
    }

    /// Exhaustive: adding a RedemptionResult variant breaks compilation here until it is mapped.
    private static ResponseEntity<Object> toResponse(RedemptionResult result) {
        return switch (result) {
            case Redeemed redeemed -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(RedemptionResponse.from(redeemed));
            case CouponNotFound(var code) -> Problems.response(HttpStatus.NOT_FOUND,
                    ErrorCode.COUPON_NOT_FOUND, "Coupon '%s' does not exist.".formatted(code));
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
            case GeolocationUnavailable(var code) -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(Problems.problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.GEOLOCATION_UNAVAILABLE,
                            "The country of your request could not be verified. Try again shortly."));
        };
    }
}
