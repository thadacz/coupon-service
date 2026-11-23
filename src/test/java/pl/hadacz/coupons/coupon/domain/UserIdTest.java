package pl.hadacz.coupons.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"user-42", "a", "jan.kowalski@example.com", "auth0:123"})
    @DisplayName("accepts common identifier formats")
    void acceptsCommonFormats(String raw) {
        assertThat(UserId.of(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "user 42", "<script>"})
    @DisplayName("rejects blank or unsafe identifiers")
    void rejectsInvalid(String raw) {
        assertThatThrownBy(() -> UserId.of(raw)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("rejects identifiers longer than 64 characters")
    void rejectsTooLong() {
        assertThatThrownBy(() -> UserId.of("u".repeat(65))).isInstanceOf(DomainValidationException.class);
    }
}
