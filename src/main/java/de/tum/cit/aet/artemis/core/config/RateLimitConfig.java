package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.grid.hazelcast.Bucket4jHazelcast;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;

/**
 * Backing store for API rate-limit buckets.
 *
 * <p>
 * Rate-limit state has to be shared across nodes, otherwise each node would enforce the limit on its own and the
 * effective limit would scale with the node count. Bucket4j ships a separate storage module per backend, so exactly one
 * {@link ProxyManager} bean is contributed here depending on the configured distributed data provider. Callers depend on
 * the {@link ProxyManager} interface so they are unaffected by which one is active.
 *
 * <p>
 * Bucket state is small and short-lived, so one round trip per check is acceptable on either backend.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class RateLimitConfig {

    private static final String BUCKET_MAP_NAME = "rate-limit-buckets";

    /**
     * Hazelcast-backed bucket storage. Uses an entry processor so the read-modify-write of a bucket runs on the partition
     * owner instead of costing two round trips.
     *
     * @param hazelcastInstance the Hazelcast instance holding the bucket map
     * @return bucket storage for the Hazelcast provider
     */
    @Bean
    @Conditional(HazelcastDistributedDataCondition.class)
    public ProxyManager<String> hazelcastRateLimitProxyManager(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        IMap<String, byte[]> bucketStateMap = hazelcastInstance.getMap(BUCKET_MAP_NAME);
        return Bucket4jHazelcast.entryProcessorBasedBuilder(bucketStateMap).build();
    }

    /**
     * Redis-backed bucket storage. Uses compare-and-swap, since Redis has no equivalent of a server-side entry processor.
     *
     * @param redissonClient the Redisson client holding the bucket state
     * @return bucket storage for the Redis provider
     */
    @Bean
    @Conditional(RedisDistributedDataCondition.class)
    public ProxyManager<String> redisRateLimitProxyManager(RedissonClient redissonClient) {
        CommandAsyncExecutor commandExecutor = ((Redisson) redissonClient).getCommandExecutor();
        return Bucket4jRedisson.casBasedBuilder(commandExecutor).build();
    }

    public static BucketConfiguration perMinute(int requestsPerMinute) {
        return BucketConfiguration.builder().addLimit(limit -> limit.capacity(requestsPerMinute).refillGreedy(requestsPerMinute, Duration.ofMinutes(1))).build();
    }
}
