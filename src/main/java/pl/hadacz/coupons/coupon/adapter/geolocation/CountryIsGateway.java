package pl.hadacz.coupons.coupon.adapter.geolocation;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import pl.hadacz.coupons.coupon.adapter.geolocation.CountryIsClient.CountryIsResponse;

/// The only place that calls the provider. A separate bean from the resolver, so that the
/// retry and the concurrency limit wrap the HTTP call alone, never a cache hit.
@Component
public class CountryIsGateway {

    private final CountryIsClient client;

    public CountryIsGateway(CountryIsClient client) {
        this.client = client;
    }

    /// Retries only transient failures (5xx, I/O errors, timeouts), never 4xx answers.
    /// With virtual threads there is no thread pool to bound the calls, so `@ConcurrencyLimit`
    /// acts as a bulkhead and keeps a traffic spike from flooding the provider.
    @Retryable(includes = {HttpServerErrorException.class, ResourceAccessException.class},
            maxRetries = 2, delay = 100, jitter = 50, multiplier = 2)
    @ConcurrencyLimit(10)
    public CountryIsResponse lookup(String ip) {
        return client.lookup(ip);
    }
}
