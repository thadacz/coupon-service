package pl.hadacz.coupons.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CountryCodeTest {

    @Test
    @DisplayName("accepts an ISO code in any case")
    void acceptsIsoCodeInAnyCase() {
        assertThat(CountryCode.of(" pl ")).isEqualTo(new CountryCode("PL"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "XX", "POL", "P"})
    @DisplayName("rejects values that are not ISO 3166-1 alpha-2 codes")
    void rejectsUnknownCodes(String raw) {
        assertThatThrownBy(() -> CountryCode.of(raw)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("parses external data leniently, without throwing")
    void tryParseIsLenient() {
        assertThat(CountryCode.tryParse("de")).contains(new CountryCode("DE"));
        assertThat(CountryCode.tryParse("??")).isEmpty();
        assertThat(CountryCode.tryParse(null)).isEmpty();
    }
}
