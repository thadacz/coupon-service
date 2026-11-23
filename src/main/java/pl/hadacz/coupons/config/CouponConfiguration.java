package pl.hadacz.coupons.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pl.hadacz.coupons.coupon.application.CouponQueries;
import pl.hadacz.coupons.coupon.application.CreateCouponHandler;
import pl.hadacz.coupons.coupon.application.RedeemCouponHandler;
import pl.hadacz.coupons.coupon.application.port.CountryResolver;
import pl.hadacz.coupons.coupon.application.port.CouponRepository;

/// Wires the framework-free use cases into Spring.
@Configuration(proxyBeanMethods = false)
class CouponConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CreateCouponHandler createCouponHandler(CouponRepository coupons, Clock clock) {
        return new CreateCouponHandler(coupons, clock);
    }

    @Bean
    RedeemCouponHandler redeemCouponHandler(CouponRepository coupons, CountryResolver countries, Clock clock) {
        return new RedeemCouponHandler(coupons, countries, clock);
    }

    @Bean
    CouponQueries couponQueries(CouponRepository coupons) {
        return new CouponQueries(coupons);
    }
}
