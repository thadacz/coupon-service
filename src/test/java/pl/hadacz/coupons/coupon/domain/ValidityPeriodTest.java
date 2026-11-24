package pl.hadacz.coupons.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidityPeriodTest {

    private static final Instant START = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant END = Instant.parse("2027-01-01T00:00:00Z");
    private static final ValidityPeriod DECEMBER = new ValidityPeriod(START, END);

    @Test
    @DisplayName("includes its start and excludes its end")
    void halfOpen() {
        assertThat(DECEMBER.hasNotStartedAt(START.minusNanos(1))).isTrue();
        assertThat(DECEMBER.hasNotStartedAt(START)).isFalse();
        assertThat(DECEMBER.hasEndedBy(END.minusNanos(1))).isFalse();
        assertThat(DECEMBER.hasEndedBy(END)).isTrue();
    }

    @Test
    @DisplayName("an open side never restricts")
    void openEnds() {
        assertThat(ValidityPeriod.ALWAYS.hasNotStartedAt(Instant.MIN)).isFalse();
        assertThat(ValidityPeriod.ALWAYS.hasEndedBy(Instant.MAX)).isFalse();
        assertThat(new ValidityPeriod(START, null).hasEndedBy(Instant.MAX)).isFalse();
        assertThat(new ValidityPeriod(null, END).hasNotStartedAt(Instant.MIN)).isFalse();
    }

    @Test
    @DisplayName("must end after it starts")
    void endAfterStart() {
        assertThatThrownBy(() -> new ValidityPeriod(END, START))
                .isInstanceOf(DomainValidationException.class)
                .extracting(e -> ((DomainValidationException) e).field())
                .isEqualTo("validUntil");
        assertThatThrownBy(() -> new ValidityPeriod(START, START)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("truncates both ends, keeping open ones open")
    void truncates() {
        assertThat(new ValidityPeriod(START.plusNanos(1_500), null).truncatedTo(ChronoUnit.MICROS))
                .isEqualTo(new ValidityPeriod(START.plusNanos(1_000), null));
        assertThat(new ValidityPeriod(null, END.plusNanos(999)).truncatedTo(ChronoUnit.MICROS))
                .isEqualTo(new ValidityPeriod(null, END));
    }
}
