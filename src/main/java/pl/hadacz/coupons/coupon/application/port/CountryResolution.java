package pl.hadacz.coupons.coupon.application.port;

import pl.hadacz.coupons.coupon.domain.CountryCode;

/// "We know your country", "nobody can know it" and "we could not ask" are different answers:
/// the first two are final, the last one is a temporary failure and must never be cached.
public sealed interface CountryResolution {

    default boolean cacheable() {
        return !(this instanceof Unavailable);
    }

    record Resolved(CountryCode country) implements CountryResolution {
    }

    record Unresolvable(Reason reason) implements CountryResolution {

        public enum Reason {
            /// Loopback, private or similar address: it says nothing about the client's country.
            NON_PUBLIC_ADDRESS,
            /// The provider has no country for this address.
            NO_DATA
        }
    }

    record Unavailable(String cause) implements CountryResolution {
    }
}
