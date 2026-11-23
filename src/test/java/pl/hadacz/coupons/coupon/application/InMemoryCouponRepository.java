package pl.hadacz.coupons.coupon.application;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.application.port.RedemptionOutcome;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.UserId;

/// Fake used by the use-case tests. State-based assertions read better than mock verifications,
/// and the port is small enough to implement honestly.
class InMemoryCouponRepository implements CouponRepository {

    private final Map<CouponCode, Coupon> coupons = new ConcurrentHashMap<>();
    private final Set<String> redemptions = new HashSet<>();

    @Override
    public boolean tryCreate(Coupon coupon) {
        return coupons.putIfAbsent(coupon.code(), coupon) == null;
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return Optional.ofNullable(coupons.get(code));
    }

    /// Same contract as the database implementation; a lock is enough for an in-memory fake.
    @Override
    public synchronized RedemptionOutcome redeem(CouponCode code, UserId userId, Instant redeemedAt) {
        Coupon coupon = coupons.get(code);
        if (coupon == null) {
            throw new IllegalStateException("Coupon " + code + " does not exist");
        }
        if (!redemptions.add(code.value() + "|" + userId.value())) {
            return RedemptionOutcome.ALREADY_REDEEMED;
        }
        if (coupon.isExhausted()) {
            redemptions.remove(code.value() + "|" + userId.value());
            return RedemptionOutcome.LIMIT_REACHED;
        }
        coupons.put(code, new Coupon(coupon.code(), coupon.country(), coupon.maxUses(),
                coupon.currentUses() + 1, coupon.createdAt()));
        return RedemptionOutcome.REDEEMED;
    }

    void save(Coupon coupon) {
        coupons.put(coupon.code(), coupon);
    }
}
