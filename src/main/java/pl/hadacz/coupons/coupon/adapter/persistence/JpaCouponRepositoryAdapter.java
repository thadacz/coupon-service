package pl.hadacz.coupons.coupon.adapter.persistence;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;

@Repository
class JpaCouponRepositoryAdapter implements CouponRepository {

    static final String UNIQUE_CODE_CONSTRAINT = "uq_coupons_code";

    private final CouponJpaRepository jpa;

    JpaCouponRepositoryAdapter(CouponJpaRepository jpa) {
        this.jpa = jpa;
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
