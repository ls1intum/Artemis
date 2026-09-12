package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.cache.KeyEnumerableCache;

/**
 * Service for managing course notification caches.
 * This service provides methods to invalidate caches related to course notifications.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseNotificationCacheService {

    public static final String USER_COURSE_NOTIFICATION_CACHE = "courseNotification";

    public static final String USER_COURSE_NOTIFICATION_SETTING_SPECIFICATION_CACHE = "userCourseNotificationSettingSpecification";

    private static final String USER_COURSE_NOTIFICATION_CACHE_KEY_PREFIX = "user_course_notification_";

    private static final String USER_COURSE_NOTIFICATION_COUNT_CACHE_KEY_PREFIX = "user_course_notification_count_";

    private static final String USER_COURSE_NOTIFICATION_SETTING_SPECIFICATION_CACHE_PREFIX = "setting_specifications_";

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationCacheService.class);

    private final CacheManager cacheManager;

    public CourseNotificationCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Invalidates all course notification cache entries.
     */
    protected void clearCourseNotificationCache() {
        Cache cache = cacheManager.getCache(USER_COURSE_NOTIFICATION_CACHE);
        if (cache != null) {
            cache.clear();
            log.info("Cache '{}' has been cleared", USER_COURSE_NOTIFICATION_CACHE);
        }
    }

    /**
     * Invalidates course notification cache entries for the specified users.
     * This method will clear all cached notifications for each user in the provided set.
     *
     * @param users    A set of users whose notification caches should be invalidated
     * @param courseId the id of the course
     * @throws IllegalArgumentException if any user in the set has a null ID
     */
    @Async
    protected void invalidateCourseNotificationCacheForUsers(Set<User> users, long courseId) throws IllegalArgumentException {
        Set<String> pagePrefixes = HashSet.newHashSet(users.size());
        Set<String> countKeys = HashSet.newHashSet(users.size());
        for (User user : users) {
            if (user.getId() == null) {
                throw new IllegalArgumentException("Cannot invalidate cache for user without id.");
            }
            // The trailing separator matters: without it, course 45 also matches the keys of course 456.
            pagePrefixes.add(USER_COURSE_NOTIFICATION_CACHE_KEY_PREFIX + user.getId() + '_' + courseId + '_');
            countKeys.add(USER_COURSE_NOTIFICATION_COUNT_CACHE_KEY_PREFIX + user.getId() + '_' + courseId);
        }
        invalidateCacheForUserCourseKeys(USER_COURSE_NOTIFICATION_CACHE, pagePrefixes, countKeys);
    }

    /**
     * Invalidates course notification setting specification cache entries for the specified users.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     */
    @Async
    protected void invalidateCourseNotificationSettingSpecificationCacheForUser(long userId, long courseId) {
        invalidateCacheForKey(USER_COURSE_NOTIFICATION_SETTING_SPECIFICATION_CACHE, USER_COURSE_NOTIFICATION_SETTING_SPECIFICATION_CACHE_PREFIX + userId + '_' + courseId);
    }

    /**
     * Invalidates every paged and count entry belonging to any of the given user/course pairs, in a single pass.
     * <p>
     * Enumerating the keys is the expensive part: on a distributed cache it pulls the whole key set over the network,
     * and the cache holds one entry per user, course and page. Doing that once per user made a course-wide
     * notification quadratic - announcing to 3000 students enumerated a 40000 entry cache 3000 times. The pairs are
     * therefore collected first and matched against one enumeration.
     * <p>
     * Matching reconstructs each key's own {@code <userId>_<courseId>_} prefix and looks it up, rather than testing
     * every candidate prefix against every key, so the cost stays linear in the number of keys instead of growing
     * with the number of users as well.
     *
     * @param cacheName    the name of the cache to invalidate entries from
     * @param pagePrefixes the {@code <userId>_<courseId>_} key prefixes whose paged entries should go
     * @param countKeys    the exact count keys that should go
     */
    private void invalidateCacheForUserCourseKeys(String cacheName, Set<String> pagePrefixes, Set<String> countKeys) {
        if (pagePrefixes.isEmpty() && countKeys.isEmpty()) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cannot invalidate entries of cache '{}': the cache is not configured", cacheName);
            return;
        }
        // Spring's Cache API cannot enumerate keys, so the scan has to go through the cache that @Cacheable actually
        // writes to. Reading the distributed data provider's map directly instead would be a silent no-op:
        // @Cacheable resolves against the primary cache manager, and only that manager knows which store serves this
        // cache name. Getting that wrong once already left users looking at stale notifications.
        Set<Object> cacheKeys = cacheKeys(cache);
        if (cacheKeys == null) {
            // The paged entries cannot be found without enumeration, but the count keys are exact and work on any
            // store, so evict those rather than leaving the user with a stale unread count.
            log.warn("Cannot invalidate entries of cache '{}' by key prefix: its backing store does not expose its keys", cacheName);
            countKeys.forEach(countKey -> evict(cache, countKey));
            return;
        }
        for (Object cacheKey : cacheKeys) {
            if (cacheKey == null) {
                continue;
            }
            String key = cacheKey.toString();
            String pagePrefix = userCoursePrefixOf(key);
            if (countKeys.contains(key) || (pagePrefix != null && pagePrefixes.contains(pagePrefix))) {
                evict(cache, cacheKey);
            }
        }
    }

    /**
     * Reads back the {@code user_course_notification_<userId>_<courseId>_} prefix of a paged notification key.
     * <p>
     * Count keys carry a literal segment where the user id sits in a paged key, so they never produce a prefix that
     * can collide with one: they are matched by their exact key instead.
     *
     * @param key the cache key to inspect
     * @return the prefix, or {@code null} if the key is not a paged notification key
     */
    @Nullable
    private static String userCoursePrefixOf(String key) {
        if (!key.startsWith(USER_COURSE_NOTIFICATION_CACHE_KEY_PREFIX)) {
            return null;
        }
        int endOfUserId = key.indexOf('_', USER_COURSE_NOTIFICATION_CACHE_KEY_PREFIX.length());
        if (endOfUserId < 0) {
            return null;
        }
        int endOfCourseId = key.indexOf('_', endOfUserId + 1);
        return endOfCourseId < 0 ? null : key.substring(0, endOfCourseId + 1);
    }

    /**
     * Reads the keys a cache currently holds, as a snapshot that is safe to iterate while entries are evicted.
     *
     * @param cache the cache to read
     * @return the keys, or {@code null} if this cache's store cannot enumerate them
     */
    @Nullable
    private Set<Object> cacheKeys(Cache cache) {
        if (cache instanceof KeyEnumerableCache keyEnumerableCache) {
            return keyEnumerableCache.cacheKeys();
        }
        // Fallback for stores that expose a plain map, such as the Caffeine and concurrent-map based caches.
        if (cache.getNativeCache() instanceof Map<?, ?> nativeCache) {
            return new HashSet<>(nativeCache.keySet());
        }
        return null;
    }

    /**
     * Invalidates the cache entry with the specified key.
     *
     * @param cacheName The name of the cache to invalidate the entry from
     * @param key       The key to delete
     */
    private void invalidateCacheForKey(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cannot invalidate key '{}': cache '{}' is not configured", key, cacheName);
            return;
        }
        evict(cache, key);
    }

    /**
     * Evicts a single entry, keeping a failure for one key from abandoning the rest of an invalidation.
     *
     * @param cache the cache to evict from
     * @param key   the key to evict
     */
    private void evict(Cache cache, Object key) {
        try {
            cache.evict(key);
        }
        catch (RuntimeException e) {
            log.error("Failed to delete entry with key {} from cache '{}'", key, cache.getName(), e);
        }
    }
}
