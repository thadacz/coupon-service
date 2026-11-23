package pl.hadacz.coupons.coupon.adapter.geolocation;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "geo", types = CountryIsClient.class)
@EnableResilientMethods
@EnableCaching
class GeolocationConfiguration {

    static final String IP_COUNTRY_CACHE = "ip-country";
}
