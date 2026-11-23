package pl.hadacz.coupons.coupon.adapter.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

interface CouponJpaRepository extends JpaRepository<CouponEntity, UUID> {

    Optional<CouponEntity> findByCode(String code);

    /// Records the redemption unless this user already has one for this coupon.
    /// `ON CONFLICT DO NOTHING` reports a duplicate as 0 affected rows instead of an error,
    /// so the surrounding transaction stays usable. A concurrent insert of the same pair
    /// waits for the other transaction and then conflicts or proceeds, depending on its outcome.
    ///
    /// Must run inside the redemption transaction (MANDATORY), never on its own.
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at)
            SELECT c.id, :userId, :redeemedAt FROM coupons c WHERE c.code = :code
            ON CONFLICT (coupon_id, user_id) DO NOTHING
            """)
    int insertRedemptionIfAbsent(@Param("code") String code,
                                 @Param("userId") String userId,
                                 @Param("redeemedAt") Instant redeemedAt);

    /// Consumes one use only if one is left: check and increment in a single statement.
    /// Under READ COMMITTED a concurrent UPDATE waits for the row lock and PostgreSQL then
    /// re-evaluates the WHERE clause against the newest row version, so the counter can
    /// never pass the limit and no increment is lost.
    ///
    /// A bulk update bypasses the persistence context. That is safe here because the
    /// redemption transaction never loads a CouponEntity.
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query("""
            UPDATE CouponEntity c
               SET c.currentUses = c.currentUses + 1
             WHERE c.code = :code
               AND c.currentUses < c.maxUses
            """)
    int incrementUsageIfBelowLimit(@Param("code") String code);
}
