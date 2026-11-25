package pl.hadacz.coupons.coupon.adapter.geolocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;
import com.maxmind.db.Reader;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import pl.hadacz.coupons.coupon.application.port.CountryResolution;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Resolved;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unresolvable;
import pl.hadacz.coupons.coupon.application.port.CountryResolver;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.IpAddress;

/// Countries from a local copy of DB-IP's free "IP to Country Lite" database (CC BY 4.0), in the
/// standard MaxMind DB format and read with MaxMind's own reader.
///
/// The lookup happens in memory, so it scales with the service: no rate limit, no network latency,
/// no provider that can be down, and client IPs, which are personal data, never leave the service.
/// DB-IP publishes a new file every month; it reaches production with the next image build.
@Component
class IpDatabaseCountryResolver implements CountryResolver {

    private final Reader database;

    /// Reads the whole file (about 8 MB) into memory once. A missing or corrupt file stops the
    /// startup: without it no coupon could be redeemed. The reader is safe for concurrent lookups.
    IpDatabaseCountryResolver(@Value("${coupons.geolocation.database}") Resource file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            this.database = new Reader(in);
        }
    }

    @Override
    public CountryResolution resolve(IpAddress ip) {
        // Private and loopback addresses say nothing about the client's country, whatever a file lists.
        if (!ip.isPublic()) {
            return new Unresolvable(Unresolvable.Reason.NON_PUBLIC_ADDRESS);
        }
        return isoCodeOf(ip)
                .flatMap(CountryCode::tryParse)
                .<CountryResolution>map(Resolved::new)
                .orElseGet(() -> new Unresolvable(Unresolvable.Reason.NO_DATA));
    }

    private Optional<String> isoCodeOf(IpAddress ip) {
        try {
            return Optional.ofNullable(database.get(ip.address(), Entry.class))
                    .map(Entry::country)
                    .map(Country::isoCode);
        } catch (IOException e) {
            // Only a corrupt file fails a lookup: a bug to see, not a country to guess.
            throw new UncheckedIOException("IP database lookup failed", e);
        }
    }

    /// The part of a database entry this service reads: `{"country": {"iso_code": "PL", ...}, ...}`.
    /// Public, because MaxMind's reader (another module) creates them through their constructors.
    public record Entry(@Nullable Country country) {

        @MaxMindDbConstructor
        public Entry(@MaxMindDbParameter(name = "country") @Nullable Country country) {
            this.country = country;
        }
    }

    public record Country(@Nullable String isoCode) {

        @MaxMindDbConstructor
        public Country(@MaxMindDbParameter(name = "iso_code") @Nullable String isoCode) {
            this.isoCode = isoCode;
        }
    }
}
