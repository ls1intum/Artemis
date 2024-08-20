package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

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
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

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

    private final ServerProperties serverProperties;

    // the discovery service client that connects against the registration (jhipster registry) so that multiple server nodes can find each other to synchronize using Hazelcast
    private final DiscoveryClient discoveryClient;

    // the service registry, in our current deployment this is the jhipster registry which offers a Eureka Server under the hood
    @Nullable
    private final Registration registration;

    private final ApplicationContext applicationContext;

    private final Environment env;

    private final RedissonClient redissonClient;

    private RedisClient redisClient;    // lazy init

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username}")
    private String redisUsername;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    // NOTE: the registration is optional
    public CacheConfiguration(ServerProperties serverProperties, DiscoveryClient discoveryClient, ApplicationContext applicationContext,
            @Autowired(required = false) @Nullable Registration registration, @Autowired(required = false) @Nullable GitProperties gitProperties,
            @Autowired(required = false) @Nullable BuildProperties buildProperties, Environment env, RedissonClient redissonClient) {
        this.serverProperties = serverProperties;
        this.discoveryClient = discoveryClient;
        this.applicationContext = applicationContext;
        this.registration = registration;
        this.gitProperties = gitProperties;
        this.buildProperties = buildProperties;
        this.env = env;
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
        config.setClientName("build-agent-1");          // TODO: read from yml
        this.redisClient = RedisClient.create(config);
        return this.redisClient;
    }

    @Bean
    public KeyGenerator keyGenerator() {
        return new PrefixedKeyGenerator(this.gitProperties, this.buildProperties);
    }

    // TODO: define settings for Redisson here: files, eviction policy, max size, time to live
}
