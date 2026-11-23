package pl.hadacz.coupons.coupon.application;

import java.util.Optional;

import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;

/// Read side. Kept apart from the commands so it can later read from a replica
/// without touching the write path.
public final class CouponQueries {

    private final CouponRepository coupons;

    public CouponQueries(CouponRepository coupons) {
        this.coupons = coupons;
    }

    public Optional<Coupon> findByCode(CouponCode code) {
        return coupons.findByCode(code);
    }
}
