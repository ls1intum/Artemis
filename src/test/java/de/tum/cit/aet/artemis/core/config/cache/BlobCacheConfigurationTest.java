package de.tum.cit.aet.artemis.core.config.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * The blob cache is bounded by bytes rather than entry count, so the weigher decides how much fits. These tests store the
 * value shapes the blob caches actually hold and confirm each is admitted and readable.
 */
class BlobCacheConfigurationTest {

    private CacheManager blobCacheManager;

    @BeforeEach
    void setUp() {
        blobCacheManager = new BlobCacheConfiguration().blobCacheManager(64L * 1024 * 1024, 3600);
    }

    @Test
    void shouldServeEveryDeclaredBlobCache() {
        for (String cacheName : BlobCacheConfiguration.BLOB_CACHE_NAMES) {
            assertThat(blobCacheManager.getCache(cacheName)).as("%s must be available", cacheName).isNotNull();
        }
    }

    @Test
    void shouldStoreAndReturnByteArrayValues() {
        Cache cache = blobCacheManager.getCache("files");
        byte[] fileContent = new byte[4096];

        cache.put("some/path.pdf", fileContent);

        assertThat(cache.get("some/path.pdf")).isNotNull();
        assertThat(cache.get("some/path.pdf").get()).isSameAs(fileContent);
    }

    @Test
    void shouldStoreAndReturnStringValues() {
        Cache cache = blobCacheManager.getCache("plantUmlSvg");

        cache.put("diagram", "<svg>rendered</svg>");

        assertThat(cache.get("diagram")).isNotNull();
        assertThat(cache.get("diagram").get()).isEqualTo("<svg>rendered</svg>");
    }

    /**
     * Values of an unexpected shape must still be admitted with a nominal weight rather than breaking the weigher.
     */
    @Test
    void shouldStoreValuesOfOtherShapes() {
        Cache cache = blobCacheManager.getCache("plantUmlPng");

        cache.put("key", Integer.valueOf(7));

        assertThat(cache.get("key")).isNotNull();
        assertThat(cache.get("key").get()).isEqualTo(7);
    }

    @Test
    void shouldEvictStoredEntries() {
        Cache cache = blobCacheManager.getCache("files");
        cache.put("path", new byte[16]);

        cache.evictIfPresent("path");

        assertThat(cache.get("path")).isNull();
    }
}
