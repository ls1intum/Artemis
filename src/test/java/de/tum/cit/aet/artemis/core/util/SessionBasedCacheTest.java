package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Unit tests for {@link SessionBasedCache}.
 * Tests the generic session-based caching operations for session-keyed data.
 */
@ExtendWith(MockitoExtension.class)
class SessionBasedCacheTest {

    private static final String TEST_CACHE_NAME = "test-session-cache";

    private static final String SESSION_ID = "session_123";

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private SessionBasedCache<String> stringCache;

    @BeforeEach
    void setUp() {
        stringCache = new SessionBasedCache<>(cacheManager, TEST_CACHE_NAME);
    }

    @Nested
    class Get {

        @Test
        void shouldReturnNullWhenCacheNotFound() {
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(null);

            List<String> result = stringCache.get(SESSION_ID);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullWhenNoDataForSession() {
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);
            when(cache.get(SESSION_ID, List.class)).thenReturn(null);

            List<String> result = stringCache.get(SESSION_ID);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnCachedListWhenDataExists() {
            List<String> cachedData = List.of("item1", "item2", "item3");
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);
            when(cache.get(SESSION_ID, List.class)).thenReturn(cachedData);

            List<String> result = stringCache.get(SESSION_ID);

            assertThat(result).isNotNull();
            assertThat(result).hasSize(3);
            assertThat(result).containsExactly("item1", "item2", "item3");
        }

        @Test
        void shouldReturnEmptyListWhenCachedEmptyList() {
            List<String> cachedData = List.of();
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);
            when(cache.get(SESSION_ID, List.class)).thenReturn(cachedData);

            List<String> result = stringCache.get(SESSION_ID);

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Put {

        @Test
        void shouldNotPutWhenCacheNotFound() {
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(null);
            List<String> data = List.of("item1");

            stringCache.put(SESSION_ID, data);

            verify(cacheManager).getCache(TEST_CACHE_NAME);
        }

        @Test
        void shouldPutDataIntoCache() {
            List<String> data = List.of("item1", "item2");
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);

            stringCache.put(SESSION_ID, data);

            verify(cache).put(SESSION_ID, data);
        }

        @Test
        void shouldPutEmptyListIntoCache() {
            List<String> data = List.of();
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);

            stringCache.put(SESSION_ID, data);

            verify(cache).put(SESSION_ID, data);
        }

        @Test
        void shouldPutMutableListIntoCache() {
            List<String> data = new ArrayList<>();
            data.add("item1");
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);

            stringCache.put(SESSION_ID, data);

            verify(cache).put(SESSION_ID, data);
        }
    }

    @Nested
    class Evict {

        @Test
        void shouldNotEvictWhenCacheNotFound() {
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(null);

            stringCache.evict(SESSION_ID);

            verify(cacheManager).getCache(TEST_CACHE_NAME);
        }

        @Test
        void shouldEvictSessionFromCache() {
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);

            stringCache.evict(SESSION_ID);

            verify(cache).evict(SESSION_ID);
        }

        @Test
        void shouldEvictNonExistentSessionWithoutError() {
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);

            stringCache.evict("non_existent_session");

            verify(cache).evict("non_existent_session");
        }
    }

    @Nested
    class MultipleSessionIsolation {

        @Test
        void shouldIsolateDifferentSessions() {
            String session1 = "session_1";
            String session2 = "session_2";
            List<String> data1 = List.of("session1_item");
            List<String> data2 = List.of("session2_item");

            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);
            when(cache.get(session1, List.class)).thenReturn(data1);
            when(cache.get(session2, List.class)).thenReturn(data2);

            List<String> result1 = stringCache.get(session1);
            List<String> result2 = stringCache.get(session2);

            assertThat(result1).containsExactly("session1_item");
            assertThat(result2).containsExactly("session2_item");
        }

        @Test
        void shouldEvictOnlySpecificSession() {
            String session1 = "session_1";
            String session2 = "session_2";

            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);

            stringCache.evict(session1);

            verify(cache).evict(session1);
            verify(cache, never()).evict(session2);
        }
    }

    @Nested
    class GenericTypeHandling {

        @Test
        void shouldHandleIntegerType() {
            SessionBasedCache<Integer> intCache = new SessionBasedCache<>(cacheManager, "int-cache");
            Cache intCacheMock = mock(Cache.class);
            List<Integer> data = List.of(1, 2, 3);

            when(cacheManager.getCache("int-cache")).thenReturn(intCacheMock);
            when(intCacheMock.get(SESSION_ID, List.class)).thenReturn(data);

            List<Integer> result = intCache.get(SESSION_ID);

            assertThat(result).containsExactly(1, 2, 3);
        }

        @Test
        void shouldHandleComplexObjectType() {
            record TestObject(String name, int value) {
            }

            SessionBasedCache<TestObject> objectCache = new SessionBasedCache<>(cacheManager, "object-cache");
            Cache objectCacheMock = mock(Cache.class);
            List<TestObject> data = List.of(new TestObject("test1", 1), new TestObject("test2", 2));

            when(cacheManager.getCache("object-cache")).thenReturn(objectCacheMock);
            when(objectCacheMock.get(SESSION_ID, List.class)).thenReturn(data);

            List<TestObject> result = objectCache.get(SESSION_ID);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().name()).isEqualTo("test1");
            assertThat(result.get(1).value()).isEqualTo(2);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void shouldHandleNullSessionId() {
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);
            when(cache.get(null, List.class)).thenReturn(null);

            List<String> result = stringCache.get(null);

            assertThat(result).isNull();
        }

        @Test
        void shouldHandleEmptySessionId() {
            String emptySession = "";
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);
            when(cache.get(emptySession, List.class)).thenReturn(null);

            List<String> result = stringCache.get(emptySession);

            assertThat(result).isNull();
        }

        @Test
        void shouldHandleLongSessionId() {
            String longSession = "a".repeat(1000);
            List<String> data = List.of("item");
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);
            when(cache.get(longSession, List.class)).thenReturn(data);

            List<String> result = stringCache.get(longSession);

            assertThat(result).containsExactly("item");
        }

        @Test
        void shouldHandleSpecialCharactersInSessionId() {
            String specialSession = "session_@#$%^&*()_+=[]{}|;':\",./<>?";
            List<String> data = List.of("item");
            when(cacheManager.getCache(TEST_CACHE_NAME)).thenReturn(cache);
            when(cache.get(specialSession, List.class)).thenReturn(data);

            List<String> result = stringCache.get(specialSession);

            assertThat(result).containsExactly("item");
        }
    }
}
