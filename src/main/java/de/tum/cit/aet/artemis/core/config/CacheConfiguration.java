package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;

import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import tech.jhipster.config.cache.PrefixedKeyGenerator;

@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Configuration
@EnableCaching
public class CacheConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    @Nullable
    private final GitProperties gitProperties;

    @Nullable
    private final BuildProperties buildProperties;

    private final RedissonClient redissonClient;

    /**
     * We need this, because the redissonClient (either directly or through redisConnectionFactory) does not support the client list command yet,
     * also see RedissonConnection.getClientList() throws UnsupportedOperationException
     */
    private RedisClient redisClient;    // lazy init

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username}")
    private String redisUsername;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${spring.data.redis.client-name}")
    private String redisClientName;

    // NOTE: the registration is optional
    public CacheConfiguration(@Autowired(required = false) @Nullable GitProperties gitProperties, @Autowired(required = false) @Nullable BuildProperties buildProperties,
            RedissonClient redissonClient) {
        this.gitProperties = gitProperties;
        this.buildProperties = buildProperties;
        this.redissonClient = redissonClient;
    }

    @PreDestroy
    public void destroy() {
        log.info("Closing Cache Manager");
        redissonClient.shutdown();
        redisClient.shutdown();
    }

    @Bean
    public CacheManager cacheManager() {
        log.debug("Starting RedisCacheManager");
        return new RedissonSpringCacheManager(redissonClient);
    }

    @Bean
    public RedisClient redisClient() {
        RedisClientConfig config = new RedisClientConfig();
        config.setAddress("redis://" + redisHost + ":" + redisPort);
        config.setUsername(redisUsername);
        config.setPassword(redisPassword);
        config.setClientName(redisClientName);
        this.redisClient = RedisClient.create(config);
        return this.redisClient;
    }

    @Bean
    public KeyGenerator keyGenerator() {
        return new PrefixedKeyGenerator(this.gitProperties, this.buildProperties);
    }
}
