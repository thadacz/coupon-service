package pl.hadacz.coupons.coupon.application;

import java.util.Optional;

import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.Redemption;
import pl.hadacz.coupons.coupon.domain.UserId;

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

    /// Lets a client that lost the answer to a redemption (timeout, dropped connection)
    /// find out whether it went through, instead of retrying blindly.
    public Optional<Redemption> findRedemption(CouponCode code, UserId userId) {
        return coupons.findRedemption(code, userId);
    }
}
