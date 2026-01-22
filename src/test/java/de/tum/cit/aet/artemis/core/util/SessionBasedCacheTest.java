package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class SessionBasedCacheTest {

    private static final String CACHE_NAME = "test-cache";

    private static final String SESSION_ID = "session-1";

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private SessionBasedCache<String> sessionCache;

    @BeforeEach
    void setUp() {
        sessionCache = new SessionBasedCache<>(cacheManager, CACHE_NAME);
    }

    @Test
    void get_returnsNull_whenCacheDoesNotExist() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

        assertThat(sessionCache.get(SESSION_ID)).isNull();
    }

    @Test
    void get_returnsNull_whenNoValueStoredForSession() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(SESSION_ID, List.class)).thenReturn(null);

        assertThat(sessionCache.get(SESSION_ID)).isNull();
    }

    @Test
    void get_returnsCachedValue_whenPresent() {
        List<String> data = List.of("a", "b");

        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(SESSION_ID, List.class)).thenReturn(data);

        assertThat(sessionCache.get(SESSION_ID)).isEqualTo(data);
    }

    @Test
    void put_storesValue_whenCacheExists() {
        List<String> data = List.of("x");

        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        sessionCache.put(SESSION_ID, data);

        verify(cache).put(SESSION_ID, data);
    }

    @Test
    void put_doesNothing_whenCacheDoesNotExist() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

        sessionCache.put(SESSION_ID, List.of("x"));

        verifyNoInteractions(cache);
    }

    @Test
    void evict_removesValue_whenCacheExists() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        sessionCache.evict(SESSION_ID);

        verify(cache).evict(SESSION_ID);
    }

    @Test
    void evict_doesNothing_whenCacheDoesNotExist() {
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

        sessionCache.evict(SESSION_ID);

        verifyNoInteractions(cache);
    }
}
