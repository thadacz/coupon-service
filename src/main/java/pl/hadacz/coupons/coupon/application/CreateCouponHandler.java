package pl.hadacz.coupons.coupon.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import pl.hadacz.coupons.coupon.application.CreateCouponResult.CodeAlreadyExists;
import pl.hadacz.coupons.coupon.application.CreateCouponResult.Created;
import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.domain.Coupon;

public final class CreateCouponHandler {

    private final CouponRepository coupons;
    private final Clock clock;

    public CreateCouponHandler(CouponRepository coupons, Clock clock) {
        this.coupons = coupons;
        this.clock = clock;
    }

    public CreateCouponResult handle(CreateCouponCommand command) {
        // PostgreSQL stores microseconds; truncating keeps the returned value equal to the stored one.
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Coupon coupon = Coupon.create(command.code(), command.country(), command.maxUses(), createdAt);

        return coupons.tryCreate(coupon)
                ? new Created(coupon)
                : new CodeAlreadyExists(command.code());
    }
}
