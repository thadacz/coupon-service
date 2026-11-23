package pl.hadacz.coupons.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IpAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {"83.0.0.1", "8.8.8.8", "2a00:1450:4001:80b::200e"})
    @DisplayName("treats public IPv4 and IPv6 addresses as geolocatable")
    void publicAddresses(String literal) {
        assertThat(IpAddress.of(literal).isPublic()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "10.1.2.3", "172.17.0.1", "192.168.1.10", "169.254.0.5",
            "100.64.0.1", "0.0.0.0", "::1", "fe80::1", "fd12:3456::1"})
    @DisplayName("treats loopback, private, link-local and CGNAT addresses as not geolocatable")
    void nonPublicAddresses(String literal) {
        assertThat(IpAddress.of(literal).isPublic()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "localhost", "example.com", "999.1.1.1"})
    @DisplayName("accepts only IP literals and never resolves host names")
    void rejectsNonLiterals(String raw) {
        assertThatThrownBy(() -> IpAddress.of(raw)).isInstanceOf(DomainValidationException.class);
    }
}
