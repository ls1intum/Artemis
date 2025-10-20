package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.grid.hazelcast.Bucket4jHazelcast;
import io.github.bucket4j.grid.hazelcast.HazelcastProxyManager;

@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class RateLimitConfig {

    @Bean
    public IMap<String, byte[]> bucketStateMap(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        return hazelcastInstance.getMap("rate-limit-buckets");
    }

    @Bean
    public HazelcastProxyManager<String> hazelcastProxyManager(IMap<String, byte[]> bucketStateMap) {
        return Bucket4jHazelcast.entryProcessorBasedBuilder(bucketStateMap).build();
    }

    public static BucketConfiguration perMinute(int rpm) {
        return BucketConfiguration.builder().addLimit(limit -> limit.capacity(rpm).refillGreedy(rpm, Duration.ofMinutes(1))).build();
    }
}
