package pl.hadacz.coupons.coupon.adapter.web;

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
import pl.hadacz.coupons.coupon.application.CreateCouponCommand;
import pl.hadacz.coupons.coupon.application.CreateCouponHandler;
import pl.hadacz.coupons.coupon.application.CreateCouponResult.CodeAlreadyExists;
import pl.hadacz.coupons.coupon.application.CreateCouponResult.Created;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.CouponCode;

@RestController
@RequestMapping(path = "/api/v1/coupons", produces = MediaType.APPLICATION_JSON_VALUE)
class CouponController {

    private final CreateCouponHandler createCoupon;
    private final CouponQueries queries;

    CouponController(CreateCouponHandler createCoupon, CouponQueries queries) {
        this.createCoupon = createCoupon;
        this.queries = queries;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> create(@Valid @RequestBody CreateCouponRequest request) {
        var command = new CreateCouponCommand(
                CouponCode.of(request.code()), CountryCode.of(request.country()), request.maxUses());

        return switch (createCoupon.handle(command)) {
            case Created(var coupon) -> ResponseEntity
                    .created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                            .path("/{code}").buildAndExpand(coupon.code().value()).toUri())
                    .body(CouponResponse.from(coupon));
            case CodeAlreadyExists(var code) -> Problems.response(HttpStatus.CONFLICT,
                    ErrorCode.COUPON_CODE_ALREADY_EXISTS,
                    "Coupon '%s' already exists (codes are case-insensitive).".formatted(code));
        };
    }

    @GetMapping("/{code}")
    ResponseEntity<Object> get(@PathVariable String code) {
        CouponCode couponCode = CouponCode.of(code);
        return queries.findByCode(couponCode)
                .<ResponseEntity<Object>>map(coupon -> ResponseEntity.ok(CouponResponse.from(coupon)))
                .orElseGet(() -> Problems.response(HttpStatus.NOT_FOUND, ErrorCode.COUPON_NOT_FOUND,
                        "Coupon '%s' does not exist.".formatted(couponCode)));
    }
}
