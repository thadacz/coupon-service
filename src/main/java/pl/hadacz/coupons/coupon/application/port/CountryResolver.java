package pl.hadacz.coupons.coupon.application.port;

import pl.hadacz.coupons.coupon.domain.IpAddress;

/// Finds the country of a client IP. Implementations must not throw for provider failures:
/// those are reported as `Unavailable`, so the caller decides the policy (fail closed).
public interface CountryResolver {

    CountryResolution resolve(IpAddress ip);
}
