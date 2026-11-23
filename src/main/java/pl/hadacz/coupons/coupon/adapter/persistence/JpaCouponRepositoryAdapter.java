package pl.hadacz.coupons.coupon.adapter.persistence;

import java.time.Instant;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.application.port.RedemptionOutcome;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.UserId;

@Repository
class JpaCouponRepositoryAdapter implements CouponRepository {

    static final String UNIQUE_CODE_CONSTRAINT = "uq_coupons_code";

    private final CouponJpaRepository jpa;
    private final TransactionTemplate transaction;

    JpaCouponRepositoryAdapter(CouponJpaRepository jpa, TransactionTemplate transaction) {
        this.jpa = jpa;
        this.transaction = transaction;
    }

    @Override
    public boolean tryCreate(Coupon coupon) {
        try {
            // Flush now: a unique violation would otherwise surface only at commit,
            // outside this method, where it could no longer be told apart from other errors.
            jpa.saveAndFlush(CouponEntity.from(coupon));
            return true;
        } catch (DataIntegrityViolationException e) {
            if (violates(e, UNIQUE_CODE_CONSTRAINT)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return jpa.findByCode(code.value()).map(CouponEntity::toDomain);
    }

    /// The whole transaction is two statements and no I/O besides the database, so locks
    /// are held for milliseconds. INSERT goes first: the hot coupon row is locked only from
    /// the UPDATE until COMMIT. The FK check of the INSERT takes FOR KEY SHARE on the coupon
    /// row and the UPDATE takes FOR NO KEY UPDATE; those modes do not conflict.
    @Override
    public RedemptionOutcome redeem(CouponCode code, UserId userId, Instant redeemedAt) {
        RedemptionOutcome outcome = transaction.execute(status -> {
            if (jpa.insertRedemptionIfAbsent(code.value(), userId.value(), redeemedAt) == 0) {
                return RedemptionOutcome.ALREADY_REDEEMED;
            }
            if (jpa.incrementUsageIfBelowLimit(code.value()) == 0) {
                status.setRollbackOnly(); // undoes the INSERT above
                return RedemptionOutcome.LIMIT_REACHED;
            }
            return RedemptionOutcome.REDEEMED;
        });
        if (outcome == null) {
            throw new IllegalStateException("Redemption transaction returned no outcome");
        }
        return outcome;
    }

    private static boolean violates(Throwable error, String constraint) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
