package com.myyak.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache 설정
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String POPULAR_SUPPLEMENTS = "popularSupplements";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(POPULAR_SUPPLEMENTS);
    }
}
