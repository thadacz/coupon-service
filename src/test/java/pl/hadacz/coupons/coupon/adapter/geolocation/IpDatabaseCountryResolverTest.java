package pl.hadacz.coupons.coupon.adapter.geolocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import pl.hadacz.coupons.coupon.application.port.CountryResolution.Resolved;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unresolvable;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.IpAddress;

/// Runs against MaxMind's official test database (same format and record shape as DB-IP's file),
/// whose networks are fixed, so the expected countries never change. See src/test/resources/geo.
class IpDatabaseCountryResolverTest {

    private final IpDatabaseCountryResolver resolver;

    IpDatabaseCountryResolverTest() throws IOException {
        resolver = new IpDatabaseCountryResolver(new ClassPathResource("geo/GeoIP2-Country-Test.mmdb"));
    }

    @Test
    @DisplayName("resolves IPv4 and IPv6 addresses found in the database")
    void resolves() {
        assertThat(resolver.resolve(IpAddress.of("55.0.0.4"))).isEqualTo(new Resolved(CountryCode.of("DE")));
        assertThat(resolver.resolve(IpAddress.of("2a02:d100::1"))).isEqualTo(new Resolved(CountryCode.of("PL")));
    }

    @Test
    @DisplayName("has no data for a public address the database does not cover")
    void noData() {
        assertThat(resolver.resolve(IpAddress.of("8.8.8.8")))
                .isEqualTo(new Unresolvable(Unresolvable.Reason.NO_DATA));
    }

    @Test
    @DisplayName("has no data for an entry without a country, such as an anycast network")
    void entryWithoutCountry() {
        // 214.1.1.0/24 is in the test database with a continent but no country.
        assertThat(resolver.resolve(IpAddress.of("214.1.1.1")))
                .isEqualTo(new Unresolvable(Unresolvable.Reason.NO_DATA));
    }

    @Test
    @DisplayName("never resolves private or loopback addresses")
    void nonPublic() {
        assertThat(resolver.resolve(IpAddress.of("10.1.2.3")))
                .isEqualTo(new Unresolvable(Unresolvable.Reason.NON_PUBLIC_ADDRESS));
        assertThat(resolver.resolve(IpAddress.of("::1")))
                .isEqualTo(new Unresolvable(Unresolvable.Reason.NON_PUBLIC_ADDRESS));
    }

    @Test
    @DisplayName("refuses to start with a missing database instead of running without geolocation")
    void missingFile() {
        assertThatThrownBy(() -> new IpDatabaseCountryResolver(new ClassPathResource("geo/missing.mmdb")))
                .isInstanceOf(IOException.class);
    }
}
