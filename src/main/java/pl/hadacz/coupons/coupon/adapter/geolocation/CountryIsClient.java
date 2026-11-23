package pl.hadacz.coupons.coupon.adapter.geolocation;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/// Declarative client of https://api.country.is (free, HTTPS, no API key, commercial use allowed).
/// Spring creates the implementation; base URL and timeouts come from `spring.http.serviceclient.geo.*`.
@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
public interface CountryIsClient {

    /// 200 with the country, 404 when there is no data, 400 for an invalid IP, 429 above 10 requests/s.
    @GetExchange("/{ip}")
    CountryIsResponse lookup(@PathVariable String ip);

    record CountryIsResponse(String ip, String country) {
    }
}
