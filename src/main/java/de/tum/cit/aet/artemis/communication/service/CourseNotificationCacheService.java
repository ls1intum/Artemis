package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;

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

    private final DistributedDataAccessService distributedDataAccessService;

    public CourseNotificationCacheService(CacheManager cacheManager, DistributedDataAccessService distributedDataAccessService) {
        this.cacheManager = cacheManager;
        this.distributedDataAccessService = distributedDataAccessService;
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
        for (User user : users) {
            if (user.getId() == null) {
                throw new IllegalArgumentException("Cannot invalidate cache for user without id.");
            }

            invalidateCacheForKeyStartingWith(USER_COURSE_NOTIFICATION_CACHE, USER_COURSE_NOTIFICATION_CACHE_KEY_PREFIX + user.getId() + '_' + courseId);
            invalidateCacheForKey(USER_COURSE_NOTIFICATION_CACHE, USER_COURSE_NOTIFICATION_COUNT_CACHE_KEY_PREFIX + user.getId() + '_' + courseId);
        }
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
     * Invalidates cache entries whose keys start with the specified prefix.
     * Since we cannot tag our cache, this method is used to clear paging-related caches
     * by matching and removing entries with keys that start with the given prefix.
     *
     * @param cache The name of the cache to invalidate entries from
     * @param key   The key prefix to match against cache entries
     */
    private void invalidateCacheForKeyStartingWith(String cache, String key) {
        DistributedMap<Object, Object> cacheMap = distributedDataAccessService.getDistributedMap(cache);

        Set<Object> keys = cacheMap.keySet();
        keys.stream().filter(k -> k.toString().startsWith(key)).forEach(k -> {
            try {
                cacheMap.remove(k);
            }
            catch (ClassCastException | NullPointerException e) {
                log.error("Failed to delete cache entry with key: {}", k, e);
            }
        });
    }

    /**
     * Invalidates cache entries with a specified key.
     *
     * @param cache The name of the cache to invalidate entries from
     * @param key   The key to delete
     */
    private void invalidateCacheForKey(String cache, String key) {
        DistributedMap<Object, Object> cacheMap = distributedDataAccessService.getDistributedMap(cache);

        try {
            cacheMap.remove(key);
        }
        catch (ClassCastException | NullPointerException e) {
            // Nothing needs to be done
        }
    }
}
