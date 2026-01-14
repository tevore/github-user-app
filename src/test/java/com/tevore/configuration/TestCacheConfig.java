package com.tevore.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@TestConfiguration
@Profile("test")
public class TestCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "githubUsers",
                "githubUserRepos"
        );

        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofSeconds(5)) // short TTL for tests
                .recordStats()
        );

        return manager;
    }
}
