package de.tum.cit.aet.artemis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.support.NoOpCacheManager;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

/**
 * Exercises cache invalidation against a real provider rather than a mocked backend map.
 *
 * <p>
 * The previous version verified that {@code delete} had been called on a mocked Hazelcast {@code IMap}, which asserted
 * mock interactions rather than the outcome. Asserting on the surviving keys of a real map instead catches a prefix scan
 * that matches too little or too much, which is the failure mode that actually matters here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseNotificationCacheServiceTest {

    private CourseNotificationCacheService courseNotificationCacheService;

    private LocalDataProviderService distributedDataProvider;

    private static final String CACHE_NAME = CourseNotificationCacheService.USER_COURSE_NOTIFICATION_CACHE;

    private static final long COURSE_ID = 123L;

    @BeforeEach
    void setUp() {
        distributedDataProvider = new LocalDataProviderService();
        // The Spring cache itself is not under test here; only the key-level invalidation of the backing map is.
        courseNotificationCacheService = new CourseNotificationCacheService(new NoOpCacheManager(), distributedDataProvider);
    }

    /**
     * @return the map that backs the notification cache, addressed by cache name
     */
    private DistributedMap<Object, Object> backingCacheMap() {
        return distributedDataProvider.getMap(CACHE_NAME);
    }

    /**
     * Seeds the paging and count entries a user would have cached for a course.
     *
     * @param userId   the user id
     * @param courseId the course id
     */
    private void seedCacheEntries(Long userId, long courseId) {
        DistributedMap<Object, Object> cacheMap = backingCacheMap();
        cacheMap.put("user_course_notification_" + userId + "_" + courseId + "_page0", "cached");
        cacheMap.put("user_course_notification_" + userId + "_" + courseId + "_page1", "cached");
        cacheMap.put("user_course_notification_count_" + userId + "_" + courseId, "cached");
    }

    @Test
    void shouldInvalidateCacheForUserWhenCacheIsSet() {
        User user = createUserWithId(1L);
        seedCacheEntries(user.getId(), COURSE_ID);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(Set.of(user), COURSE_ID);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(backingCacheMap().keySet()).isEmpty());
    }

    @Test
    void shouldInvalidateCacheForMultipleUsersWhenAllUsersHaveCachedEntries() {
        User user1 = createUserWithId(1L);
        User user2 = createUserWithId(2L);
        seedCacheEntries(user1.getId(), COURSE_ID);
        seedCacheEntries(user2.getId(), COURSE_ID);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(Set.of(user1, user2), COURSE_ID);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(backingCacheMap().keySet()).isEmpty());
    }

    /**
     * Guards against the prefix scan being too greedy: entries for an unrelated course must survive.
     */
    @Test
    void shouldKeepEntriesOfOtherCoursesAndUsers() {
        User user = createUserWithId(1L);
        seedCacheEntries(user.getId(), COURSE_ID);
        seedCacheEntries(user.getId(), 999L);
        seedCacheEntries(2L, COURSE_ID);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(Set.of(user), COURSE_ID);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(backingCacheMap().keySet()).noneMatch(key -> key.toString().contains("_1_" + COURSE_ID));
            assertThat(backingCacheMap().keySet()).anyMatch(key -> key.toString().contains("_1_999"));
            assertThat(backingCacheMap().keySet()).anyMatch(key -> key.toString().contains("_2_" + COURSE_ID));
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
        User untouched = createUserWithId(1L);
        seedCacheEntries(untouched.getId(), COURSE_ID);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(new HashSet<>(), COURSE_ID);

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(backingCacheMap().keySet()).hasSize(3));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, 1L, 999L })
    void shouldInvalidateCacheForDifferentCourseIdsWhenUserHasId(long courseId) {
        User user = createUserWithId(1L);
        seedCacheEntries(user.getId(), courseId);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(Set.of(user), courseId);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(backingCacheMap().keySet()).isEmpty());
    }

    private User createUserWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
