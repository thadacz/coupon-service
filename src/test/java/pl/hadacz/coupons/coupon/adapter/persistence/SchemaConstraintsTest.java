package pl.hadacz.coupons.coupon.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import pl.hadacz.coupons.support.IntegrationTest;

/// The database is the second line of defence: even SQL that bypasses the application
/// cannot break the invariants. These tests talk to the schema directly.
@IntegrationTest
class SchemaConstraintsTest {

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM coupon_redemptions").update();
        jdbc.sql("DELETE FROM coupons").update();
    }

    @Test
    @DisplayName("rejects a code that is not stored in normalized upper case")
    void rejectsNonNormalizedCode() {
        assertThatThrownBy(() -> insertCoupon("wiosna", "PL", 10, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_coupons_code_format");
    }

    @Test
    @DisplayName("rejects a second coupon with the same code")
    void rejectsDuplicateCode() {
        insertCoupon("WIOSNA", "PL", 10, 0);

        assertThatThrownBy(() -> insertCoupon("WIOSNA", "DE", 5, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_coupons_code");
    }

    @Test
    @DisplayName("rejects a usage counter above the limit")
    void rejectsUsesAboveLimit() {
        assertThatThrownBy(() -> insertCoupon("LATO", "PL", 1, 2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_coupons_uses_within_limit");
    }

    @Test
    @DisplayName("rejects a validity period that does not end after it starts")
    void rejectsInvertedValidityPeriod() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO coupons (code, country_code, max_uses, created_at, valid_from, valid_until)
                        VALUES ('ZIMA', 'PL', 10, now(), '2027-01-01T00:00:00Z', '2026-12-01T00:00:00Z')
                        """).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_coupons_validity_period");
    }

    @Test
    @DisplayName("rejects a second redemption of the same coupon by the same user")
    void rejectsDuplicateRedemption() {
        insertCoupon("JESIEN", "PL", 10, 0);
        insertRedemption("JESIEN", "user-1");

        assertThatThrownBy(() -> insertRedemption("JESIEN", "user-1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_coupon_redemptions_coupon_user");
    }

    private void insertCoupon(String code, String country, int maxUses, int currentUses) {
        jdbc.sql("""
                        INSERT INTO coupons (code, country_code, max_uses, current_uses, created_at)
                        VALUES (:code, :country, :maxUses, :currentUses, :createdAt)
                        """)
                .param("code", code)
                .param("country", country)
                .param("maxUses", maxUses)
                .param("currentUses", currentUses)
                .param("createdAt", java.sql.Timestamp.from(Instant.parse("2026-11-24T10:00:00Z")))
                .update();
    }

    private void insertRedemption(String code, String userId) {
        jdbc.sql("""
                        INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at)
                        SELECT id, :userId, now() FROM coupons WHERE code = :code
                        """)
                .param("code", code)
                .param("userId", userId)
                .update();
    }
}
