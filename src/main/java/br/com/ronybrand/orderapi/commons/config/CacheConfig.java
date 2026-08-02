package br.com.ronybrand.orderapi.commons.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine-backed cache manager for {@code @Cacheable} reads. Bounded and self-expiring, same
 * rationale as the rate limiter's counter store: never back a cache with an unbounded structure.
 */
@Configuration
public class CacheConfig {

    public static final String CUSTOMERS_CACHE = "customers";

    @Bean
    CacheManager cacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(CUSTOMERS_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(10_000));
        return cacheManager;
    }
}
