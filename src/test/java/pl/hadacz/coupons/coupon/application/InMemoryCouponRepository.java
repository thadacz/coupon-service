package pl.hadacz.coupons.coupon.application;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;

/// Fake used by the use-case tests. State-based assertions read better than mock verifications,
/// and the port is small enough to implement honestly.
class InMemoryCouponRepository implements CouponRepository {

    private final Map<CouponCode, Coupon> coupons = new ConcurrentHashMap<>();

    @Override
    public boolean tryCreate(Coupon coupon) {
        return coupons.putIfAbsent(coupon.code(), coupon) == null;
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return Optional.ofNullable(coupons.get(code));
    }
}
