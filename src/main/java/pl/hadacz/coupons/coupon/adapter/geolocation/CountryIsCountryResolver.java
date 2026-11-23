package pl.hadacz.coupons.coupon.adapter.geolocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import pl.hadacz.coupons.coupon.adapter.geolocation.CountryIsClient.CountryIsResponse;
import pl.hadacz.coupons.coupon.application.port.CountryResolution;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Resolved;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unavailable;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unresolvable;
import pl.hadacz.coupons.coupon.application.port.CountryResolver;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.IpAddress;

@Component
public class CountryIsCountryResolver implements CountryResolver {

    private static final Logger log = LoggerFactory.getLogger(CountryIsCountryResolver.class);

    private final CountryIsGateway gateway;

    public CountryIsCountryResolver(CountryIsGateway gateway) {
        this.gateway = gateway;
    }

    /// Answers are cached per IP for a day (the provider refreshes its data daily);
    /// failures are not cached, so the next request asks again.
    @Override
    @Cacheable(cacheNames = GeolocationConfiguration.IP_COUNTRY_CACHE, key = "#ip.value()",
            unless = "!#result.cacheable()")
    public CountryResolution resolve(IpAddress ip) {
        if (!ip.isPublic()) {
            return new Unresolvable(Unresolvable.Reason.NON_PUBLIC_ADDRESS);
        }
        try {
            CountryIsResponse response = gateway.lookup(ip.value());
            return CountryCode.tryParse(response == null ? null : response.country())
                    .<CountryResolution>map(Resolved::new)
                    .orElseGet(() -> new Unresolvable(Unresolvable.Reason.NO_DATA));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
            return new Unresolvable(Unresolvable.Reason.NO_DATA);
        } catch (RestClientException e) {
            // 5xx after retries, 429, timeouts, I/O errors. The IP is personal data: not logged.
            log.warn("Country provider unavailable: {}", e.getMessage());
            return new Unavailable(e.getClass().getSimpleName());
        }
    }
}
