package de.tum.cit.aet.artemis.core.config;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.AbstractCompareAndSwapBasedProxyManager;
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.AsyncCompareAndSwapOperation;
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.CompareAndSwapOperation;
import io.github.bucket4j.distributed.remote.RemoteBucketState;

/**
 * In-process bucket storage for the {@code Local} distributed data provider.
 *
 * <p>
 * The Local provider deliberately keeps all state inside one JVM, so there is nothing to synchronise and the buckets
 * live in a plain map. Rate limiting therefore behaves exactly as it does on a single node, which is the only topology
 * the Local provider supports.
 *
 * <p>
 * Bucket4j addresses its backends through compare-and-swap on a serialised bucket state. The identity comparison that
 * {@link ConcurrentMap#replace(Object, Object, Object)} performs on {@code byte[]} is exactly the semantics needed here:
 * {@link CompareAndSwapOperation#getStateData} hands out the stored array instance, so the swap succeeds if and only if
 * no other thread replaced it in the meantime.
 *
 * <p>
 * Entries are bounded by size and idle time. Keys are client identifiers such as IP addresses, so an unbounded map would
 * grow with every distinct client. Dropping an idle bucket is safe because a bucket that has not been touched for longer
 * than its refill period would have been fully refilled anyway.
 */
class LocalRateLimitProxyManager extends AbstractCompareAndSwapBasedProxyManager<String> {

    private static final Duration IDLE_RETENTION = Duration.ofMinutes(10);

    private static final long MAXIMUM_BUCKETS = 100_000;

    private final ConcurrentMap<String, byte[]> bucketStates = Caffeine.newBuilder().expireAfterAccess(IDLE_RETENTION).maximumSize(MAXIMUM_BUCKETS).<String, byte[]>build().asMap();

    LocalRateLimitProxyManager() {
        super(ClientSideConfig.getDefault());
    }

    @Override
    protected CompareAndSwapOperation beginCompareAndSwapOperation(String key) {
        return new CompareAndSwapOperation() {

            @Override
            public Optional<byte[]> getStateData(Optional<Long> timeoutNanos) {
                return Optional.ofNullable(bucketStates.get(key));
            }

            @Override
            public boolean compareAndSwap(byte[] originalData, byte[] newData, RemoteBucketState newState, Optional<Long> timeoutNanos) {
                if (originalData == null) {
                    return bucketStates.putIfAbsent(key, newData) == null;
                }
                return bucketStates.replace(key, originalData, newData);
            }
        };
    }

    @Override
    protected AsyncCompareAndSwapOperation beginAsyncCompareAndSwapOperation(String key) {
        throw new UnsupportedOperationException("The local rate limit bucket storage does not support asynchronous access");
    }

    @Override
    protected CompletableFuture<Void> removeAsync(String key) {
        throw new UnsupportedOperationException("The local rate limit bucket storage does not support asynchronous access");
    }

    @Override
    public void removeProxy(String key) {
        bucketStates.remove(key);
    }

    @Override
    public boolean isAsyncModeSupported() {
        return false;
    }
}
