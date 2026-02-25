package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;

@ExtendWith(MockitoExtension.class)
class CourseNotificationCacheServiceTest {

    private CourseNotificationCacheService courseNotificationCacheService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    @Mock
    private DistributedMap<Object, Object> cacheMap;

    private static final String CACHE_NAME = CourseNotificationCacheService.USER_COURSE_NOTIFICATION_CACHE;

    private static final long COURSE_ID = 123L;

    @BeforeEach
    void setUp() {
        courseNotificationCacheService = new CourseNotificationCacheService(cacheManager, distributedDataAccessService);
    }

    @Test
    void shouldInvalidateCacheForUserWhenCacheIsSet() {
        when(distributedDataAccessService.getDistributedMap(anyString())).thenReturn(cacheMap);

        User user = createUserWithId(1L);
        Set<User> users = Set.of(user);
        Set<Object> cacheKeys = createMockCacheKeys(user.getId(), COURSE_ID);
        when(cacheMap.keySet()).thenReturn(cacheKeys);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(users, COURSE_ID);

        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(distributedDataAccessService, times(2)).getDistributedMap(CACHE_NAME);

            ArgumentCaptor<Object> keyCaptor = ArgumentCaptor.forClass(Object.class);
            verify(cacheMap, atLeastOnce()).remove(keyCaptor.capture());

            assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.toString().startsWith("user_course_notification_1_123"));

            assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.toString().equals("user_course_notification_count_1_123"));
        });
    }

    @Test
    void shouldInvalidateCacheForMultipleUsersWhenAllUsersHaveCachedEntries() {
        when(distributedDataAccessService.getDistributedMap(anyString())).thenReturn(cacheMap);

        User user1 = createUserWithId(1L);
        User user2 = createUserWithId(2L);
        Set<User> users = Set.of(user1, user2);

        Set<Object> cacheKeys = new HashSet<>();
        cacheKeys.addAll(createMockCacheKeys(user1.getId(), COURSE_ID));
        cacheKeys.addAll(createMockCacheKeys(user2.getId(), COURSE_ID));
        when(cacheMap.keySet()).thenReturn(cacheKeys);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(users, COURSE_ID);

        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(distributedDataAccessService, times(4)).getDistributedMap(CACHE_NAME);

            ArgumentCaptor<Object> keyCaptor = ArgumentCaptor.forClass(Object.class);
            verify(cacheMap, atLeast(4)).remove(keyCaptor.capture());

            assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.toString().startsWith("user_course_notification_1_123"))
                    .anyMatch(key -> key.toString().startsWith("user_course_notification_2_123")).anyMatch(key -> key.toString().equals("user_course_notification_count_1_123"))
                    .anyMatch(key -> key.toString().equals("user_course_notification_count_2_123"));
        });
    }

    @Test
    void shouldThrowExceptionWhenUserHasNoId() {
        User userWithoutId = new User();
        Set<User> users = Set.of(userWithoutId);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(users, COURSE_ID);
            Awaitility.await().atMost(1, TimeUnit.SECONDS).until(() -> true);
        }).withMessage("Cannot invalidate cache for user without id.");
    }

    @Test
    void shouldHandleEmptyUserSetWhenInvalidatingCache() {
        Set<User> emptyUsers = new HashSet<>();

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(emptyUsers, COURSE_ID);

        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(distributedDataAccessService, never()).getDistributedMap(anyString());
            verify(cacheMap, never()).remove(any());
        });
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, 1L, 999L })
    void shouldInvalidateCacheForDifferentCourseIdsWhenUserHasId(long courseId) {
        when(distributedDataAccessService.getDistributedMap(anyString())).thenReturn(cacheMap);
        User user = createUserWithId(1L);
        Set<User> users = Set.of(user);
        Set<Object> cacheKeys = createMockCacheKeys(user.getId(), courseId);
        when(cacheMap.keySet()).thenReturn(cacheKeys);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(users, courseId);

        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(distributedDataAccessService, times(2)).getDistributedMap(CACHE_NAME);

            ArgumentCaptor<Object> keyCaptor = ArgumentCaptor.forClass(Object.class);
            verify(cacheMap, atLeastOnce()).remove(keyCaptor.capture());

            assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.toString().contains("_" + courseId));
        });
    }

    @Test
    void shouldHandleExceptionsWhenDeletingCacheEntries() {
        when(distributedDataAccessService.getDistributedMap(anyString())).thenReturn(cacheMap);
        User user = createUserWithId(1L);
        Set<User> users = Set.of(user);
        Set<Object> cacheKeys = createMockCacheKeys(user.getId(), COURSE_ID);
        when(cacheMap.keySet()).thenReturn(cacheKeys);

        doThrow(new ClassCastException("Test exception")).when(cacheMap).remove(any());

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(users, COURSE_ID);

        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(distributedDataAccessService, times(2)).getDistributedMap(CACHE_NAME);
            verify(cacheMap, atLeastOnce()).remove(any());
        });
    }

    private User createUserWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Set<Object> createMockCacheKeys(Long userId, Long courseId) {
        Set<Object> keys = new HashSet<>();
        keys.add("user_course_notification_" + userId + "_" + courseId + "_page0");
        keys.add("user_course_notification_" + userId + "_" + courseId + "_page1");
        keys.add("user_course_notification_count_" + userId + "_" + courseId);
        return keys;
    }
}
