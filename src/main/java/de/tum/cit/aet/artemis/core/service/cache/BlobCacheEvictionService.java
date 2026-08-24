package de.tum.cit.aet.artemis.core.service.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.Serial;
import java.io.Serializable;
import java.util.Optional;

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
 * Propagates blob-cache evictions to every node.
 *
 * <p>
 * The blob caches are per-node (see {@link BlobCacheConfiguration}), so an eviction performed on the node handling a
 * write would otherwise leave every other node serving the previous file content. This broadcasts the eviction so all
 * nodes drop the entry.
 *
 * <p>
 * A plain topic is deliberate: the blob caches also expire entries after a time-to-live, so a dropped broadcast
 * self-corrects within that window instead of serving stale content forever. Using a reliable topic here would add
 * retention cost for no lasting benefit.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class BlobCacheEvictionService {

    private static final Logger log = LoggerFactory.getLogger(BlobCacheEvictionService.class);

    private static final String EVICTION_TOPIC = "blobCacheEviction";

    private final CacheManager cacheManager;

    private final Optional<DistributedDataProvider> distributedDataProvider;

    private DistributedTopic<BlobCacheEviction> evictionTopic;

    public BlobCacheEvictionService(CacheManager cacheManager, Optional<DistributedDataProvider> distributedDataProvider) {
        this.cacheManager = cacheManager;
        this.distributedDataProvider = distributedDataProvider;
    }

    @PostConstruct
    public void init() {
        distributedDataProvider.ifPresent(provider -> {
            evictionTopic = provider.getTopic(EVICTION_TOPIC);
            evictionTopic.addMessageListener(this::evictLocally);
        });
    }

    /**
     * Evicts the entry on every node, including this one.
     *
     * @param cacheName the blob cache holding the entry
     * @param key       the key to evict
     */
    public void evictEverywhere(String cacheName, Object key) {
        BlobCacheEviction eviction = new BlobCacheEviction(cacheName, String.valueOf(key));
        if (evictionTopic == null) {
            // Single node, or no provider configured: a local eviction already covers every reader.
            evictLocally(eviction);
            return;
        }
        try {
            evictionTopic.publish(eviction);
        }
        catch (Exception e) {
            // Still evict locally, so the node that performed the write never serves its own stale entry.
            log.warn("Failed to broadcast blob cache eviction for {}: {}", cacheName, e.getMessage());
            evictLocally(eviction);
        }
    }

    /**
     * Drops the entry from this node's cache.
     *
     * @param eviction the eviction to apply
     */
    private void evictLocally(BlobCacheEviction eviction) {
        Cache cache = cacheManager.getCache(eviction.cacheName());
        if (cache != null) {
            cache.evictIfPresent(eviction.key());
        }
    }

    /**
     * @param cacheName the blob cache holding the entry
     * @param key       the stringified cache key
     */
    public record BlobCacheEviction(String cacheName, String key) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
