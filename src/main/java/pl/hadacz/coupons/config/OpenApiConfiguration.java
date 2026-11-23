package pl.hadacz.coupons.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// API description served at /v3/api-docs, with Swagger UI at /swagger-ui.html.
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI couponServiceApi() {
        return new OpenAPI().info(new Info()
                .title("Coupon Service")
                .version("v1")
                .description("""
                        Creating and redeeming discount coupons. Errors follow RFC 9457 (problem details) \
                        with an `errorCode` member. The client country is derived from the connection's IP \
                        (X-Forwarded-For is honoured only from trusted proxies)."""));
    }
}
