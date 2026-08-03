package de.tum.cit.aet.artemis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Exercises cache invalidation against the cache that Spring actually writes to.
 *
 * <p>
 * The cache manager used here is a {@link ConcurrentMapCacheManager}, because its {@code getNativeCache()} exposes a
 * {@link java.util.concurrent.ConcurrentMap}, the same shape Hazelcast's {@code IMap} presents. That is what the prefix
 * scan needs, so this models the production store faithfully without starting Hazelcast.
 *
 * <p>
 * Seeding and asserting both go through the Spring {@link Cache} API on purpose. An earlier version used the distributed
 * data provider's map instead, which passed while production was broken: {@code @Cacheable} resolves against the primary
 * {@code RoutingCacheManager}, which serves these caches from the Hazelcast-backed manager on every core node no matter
 * which provider is configured, so invalidating the provider's map was a no-op under the Redis and Local providers. The
 * service no longer takes a {@code DistributedDataProvider} at all, which makes that mismatch structurally impossible
 * rather than merely tested for.
 */
class CourseNotificationCacheServiceTest {

    private CourseNotificationCacheService courseNotificationCacheService;

    private ConcurrentMapCacheManager cacheManager;

    private static final String CACHE_NAME = CourseNotificationCacheService.USER_COURSE_NOTIFICATION_CACHE;

    private static final String SETTINGS_CACHE_NAME = CourseNotificationCacheService.USER_COURSE_NOTIFICATION_SETTING_SPECIFICATION_CACHE;

    private static final long COURSE_ID = 123L;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(CACHE_NAME, SETTINGS_CACHE_NAME);
        courseNotificationCacheService = new CourseNotificationCacheService(cacheManager);
    }

    /**
     * @return the keys currently held by the notification cache, read through the store backing the Spring cache
     */
    private Set<Object> cachedKeys() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        return new HashSet<>(((Map<?, ?>) cache.getNativeCache()).keySet());
    }

    /**
     * Seeds the paging and count entries a user would have cached for a course, through the Spring cache API.
     *
     * @param userId   the user id
     * @param courseId the course id
     */
    private void seedCacheEntries(Long userId, long courseId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        cache.put("user_course_notification_" + userId + "_" + courseId + "_page0", "cached");
        cache.put("user_course_notification_" + userId + "_" + courseId + "_page1", "cached");
        cache.put("user_course_notification_count_" + userId + "_" + courseId, "cached");
    }

    @Test
    void shouldInvalidateCacheForUserWhenCacheIsSet() {
        User user = createUserWithId(1L);
        seedCacheEntries(user.getId(), COURSE_ID);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(Set.of(user), COURSE_ID);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(cachedKeys()).isEmpty());
    }

    @Test
    void shouldInvalidateCacheForMultipleUsersWhenAllUsersHaveCachedEntries() {
        User user1 = createUserWithId(1L);
        User user2 = createUserWithId(2L);
        seedCacheEntries(user1.getId(), COURSE_ID);
        seedCacheEntries(user2.getId(), COURSE_ID);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(Set.of(user1, user2), COURSE_ID);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(cachedKeys()).isEmpty());
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
            assertThat(cachedKeys()).noneMatch(key -> key.toString().contains("_1_" + COURSE_ID));
            assertThat(cachedKeys()).anyMatch(key -> key.toString().contains("_1_999"));
            assertThat(cachedKeys()).anyMatch(key -> key.toString().contains("_2_" + COURSE_ID));
        });
    }

    @Test
    void shouldInvalidateTheSettingSpecificationEntryOfTheGivenUserOnly() {
        Cache settings = cacheManager.getCache(SETTINGS_CACHE_NAME);
        settings.put("setting_specifications_1_" + COURSE_ID, "cached");
        settings.put("setting_specifications_2_" + COURSE_ID, "cached");

        courseNotificationCacheService.invalidateCourseNotificationSettingSpecificationCacheForUser(1L, COURSE_ID);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(settings.get("setting_specifications_1_" + COURSE_ID)).isNull();
            assertThat(settings.get("setting_specifications_2_" + COURSE_ID)).isNotNull();
        });
    }

    @Test
    void shouldClearTheWholeNotificationCache() {
        seedCacheEntries(1L, COURSE_ID);
        seedCacheEntries(2L, 999L);

        courseNotificationCacheService.clearCourseNotificationCache();

        assertThat(cachedKeys()).isEmpty();
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

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(cachedKeys()).hasSize(3));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, 1L, 999L })
    void shouldInvalidateCacheForDifferentCourseIdsWhenUserHasId(long courseId) {
        User user = createUserWithId(1L);
        seedCacheEntries(user.getId(), courseId);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(Set.of(user), courseId);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(cachedKeys()).isEmpty());
    }

    /**
     * The exact-key eviction works on any cache manager. The prefix scan needs an enumerable store, so a cache whose
     * native store does not expose keys must degrade to a logged warning rather than throw on a notification path.
     */
    @Test
    void shouldNotFailWhenTheBackingStoreDoesNotExposeItsKeys() {
        var service = new CourseNotificationCacheService(new CaffeineCacheManager(CACHE_NAME));

        assertThatCode(() -> service.invalidateCourseNotificationCacheForUsers(Set.of(createUserWithId(1L)), COURSE_ID)).doesNotThrowAnyException();
    }

    /**
     * A cache the manager does not know must not blow up either; there is nothing to invalidate.
     */
    @Test
    void shouldNotFailWhenTheCacheIsNotConfigured() {
        var service = new CourseNotificationCacheService(new NoOpCacheManager());

        assertThatCode(() -> service.invalidateCourseNotificationCacheForUsers(Set.of(createUserWithId(1L)), COURSE_ID)).doesNotThrowAnyException();
        assertThatCode(service::clearCourseNotificationCache).doesNotThrowAnyException();
    }

    private User createUserWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
