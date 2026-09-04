package de.tum.cit.aet.artemis.core.service.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.Serial;
import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.cache.BlobCacheConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;

/**
 * Propagates the eviction of a per-node cache entry to every node.
 *
 * <p>
 * Per-node caches (the blobs of {@link BlobCacheConfiguration} and the titles of
 * {@link de.tum.cit.aet.artemis.core.config.cache.TitleCacheConfiguration}) keep a copy on each node, so an eviction
 * performed on the node handling a write would otherwise leave every other node answering with the previous value.
 * This broadcasts the eviction so all nodes drop the entry.
 *
 * <p>
 * A plain topic is deliberate: every per-node cache also expires its entries after a time-to-live, so a dropped
 * broadcast self-corrects within that window instead of serving a stale value forever. Using a reliable topic here
 * would add retention cost for no lasting benefit. That time-to-live is the price of moving a cache off the shared
 * store, and it is why a cache whose staleness would be visible for long belongs in the distributed manager instead.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class PerNodeCacheEvictionService {

    private static final Logger log = LoggerFactory.getLogger(PerNodeCacheEvictionService.class);

    private static final String EVICTION_TOPIC = "perNodeCacheEviction";

    private final CacheManager cacheManager;

    private final Optional<DistributedDataProvider> distributedDataProvider;

    private DistributedTopic<PerNodeCacheEviction> evictionTopic;

    /**
     * Identifies this instance, so that the broadcast it publishes is not applied here a second time. The eviction has
     * already happened locally and synchronously by then, and re-applying it later would only be able to drop an entry
     * that a request has legitimately read back in since.
     * <p>
     * Per instance rather than per class, so that two instances sharing one provider behave like two nodes. There is
     * one instance per node in production either way, and it lets the cross-node path be tested in a single JVM.
     */
    private final String nodeToken = UUID.randomUUID().toString();

    public PerNodeCacheEvictionService(CacheManager cacheManager, Optional<DistributedDataProvider> distributedDataProvider) {
        this.cacheManager = cacheManager;
        this.distributedDataProvider = distributedDataProvider;
    }

    @PostConstruct
    public void init() {
        distributedDataProvider.ifPresent(provider -> {
            evictionTopic = provider.getTopic(EVICTION_TOPIC);
            evictionTopic.addMessageListener(this::onEvictionPublished);
        });
    }

    /**
     * Evicts the entry on every node, including this one.
     *
     * @param cacheName the per-node cache holding the entry
     * @param key       the key to evict, in the type the cache is keyed by
     */
    public void evictEverywhere(String cacheName, Serializable key) {
        PerNodeCacheEviction eviction = new PerNodeCacheEviction(cacheName, key, nodeToken);
        // Evict here first, and synchronously. Topic delivery is asynchronous and comes back to the publisher on a
        // listener thread, so relying on it alone leaves the node that performed the write answering from its own stale
        // entry until the message arrives. The request that wrote is usually the next one to read, so that is the
        // window that matters most.
        evictLocally(eviction);
        if (evictionTopic == null) {
            // Single node, or no provider configured: the local eviction already covered every reader.
            return;
        }
        try {
            evictionTopic.publish(eviction);
        }
        catch (Exception e) {
            // This node is already consistent thanks to the local eviction above; the others fall back on the expiry.
            log.warn("Failed to broadcast per-node cache eviction for {}: {}", cacheName, e.getMessage());
        }
    }

    /**
     * Applies an eviction another node published.
     * <p>
     * A message this process published is ignored: {@link #evictEverywhere} already evicted synchronously before
     * publishing, and applying it again on this late callback would only be able to drop an entry that a request has
     * legitimately read back in since.
     *
     * @param eviction the eviction that was published
     */
    private void onEvictionPublished(PerNodeCacheEviction eviction) {
        if (nodeToken.equals(eviction.origin())) {
            return;
        }
        evictLocally(eviction);
    }

    /**
     * Drops the entry from this node's cache.
     *
     * @param eviction the eviction to apply
     */
    private void evictLocally(PerNodeCacheEviction eviction) {
        Cache cache = cacheManager.getCache(eviction.cacheName());
        if (cache != null) {
            cache.evictIfPresent(eviction.key());
        }
    }

    /**
     * @param cacheName the per-node cache holding the entry
     * @param key       the cache key, in the type the cache is keyed by, so that it matches the entry on every node
     * @param origin    identifies the publishing process, which skips its own message
     */
    public record PerNodeCacheEviction(String cacheName, Serializable key, String origin) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
