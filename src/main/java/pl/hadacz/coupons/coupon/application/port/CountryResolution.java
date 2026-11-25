package pl.hadacz.coupons.coupon.application.port;

import pl.hadacz.coupons.coupon.domain.CountryCode;

/// "We know your country" or "nobody can know it", with the reason, which the caller may log
/// but treats the same way: an unknown country is not the coupon's country (fail closed).
public sealed interface CountryResolution {

    record Resolved(CountryCode country) implements CountryResolution {
    }

    record Unresolvable(Reason reason) implements CountryResolution {

        public enum Reason {
            /// Loopback, private or similar address: it says nothing about the client's country.
            NON_PUBLIC_ADDRESS,
            /// The database has no country for this address.
            NO_DATA
        }
    }
}
