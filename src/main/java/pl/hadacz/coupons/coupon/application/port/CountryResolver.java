package pl.hadacz.coupons.coupon.application.port;

import pl.hadacz.coupons.coupon.domain.IpAddress;

/// Finds the country of a client IP.
public interface CountryResolver {

    CountryResolution resolve(IpAddress ip);
}
