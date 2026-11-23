package pl.hadacz.coupons.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CouponCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"WIOSNA", "wiosna", "Wiosna", "  wIoSnA  "})
    @DisplayName("treats codes that differ only in case or surrounding spaces as the same code")
    void normalizesCase(String raw) {
        assertThat(CouponCode.of(raw)).isEqualTo(new CouponCode("WIOSNA"));
    }

    @Test
    @DisplayName("normalizes the same way under a Turkish default locale")
    void normalizesIndependentlyOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(CouponCode.of("lipiec-i").value()).isEqualTo("LIPIEC-I");
        } finally {
            Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"AB", "THIS-CODE-IS-WAY-TOO-LONG-FOR-US-1", "WIOSNA 2026", "ZNIŻKA", "straße"})
    @DisplayName("rejects missing, too short, too long or non-ASCII codes")
    void rejectsInvalidCodes(String raw) {
        assertThatThrownBy(() -> CouponCode.of(raw))
                .isInstanceOf(DomainValidationException.class)
                .extracting(e -> ((DomainValidationException) e).field())
                .isEqualTo("code");
    }

    @Test
    @DisplayName("refuses to construct a code that is not normalized")
    void constructorRequiresNormalizedValue() {
        assertThatThrownBy(() -> new CouponCode("wiosna")).isInstanceOf(DomainValidationException.class);
    }
}
