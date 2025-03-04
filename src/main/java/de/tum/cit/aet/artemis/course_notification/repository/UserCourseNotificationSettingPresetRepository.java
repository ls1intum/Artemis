package de.tum.cit.aet.artemis.course_notification.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course_notification.domain.UserCourseNotificationSettingPreset;

/**
 * Repository for the {@link UserCourseNotificationSettingPreset} entity.
 */
@Profile(PROFILE_CORE)
@Repository
@CacheConfig(cacheNames = "userCourseNotificationSettingPreset")
public interface UserCourseNotificationSettingPresetRepository extends ArtemisJpaRepository<UserCourseNotificationSettingPreset, Long> {

    /***
     * Get the user setting preset for a given user id and course id. Cached until changed (save or delete is called).
     *
     * @param userId   to query for
     * @param courseId to query for
     *
     * @return The unique user setting preset.
     */
    @Cacheable(key = "'setting_preset_' + #userId + '_' + #courseId")
    UserCourseNotificationSettingPreset findUserCourseNotificationSettingPresetByUserIdAndCourseId(Long userId, Long courseId);

    /***
     * Saving will clear the user's cached settings.
     *
     * @param userCourseNotificationSettingPreset to store
     *
     * @return Newly stored {@link UserCourseNotificationSettingPreset}
     */
    @CacheEvict(key = "'setting_preset_' + #userCourseNotificationSettingPreset.user.id + '_' + #userCourseNotificationSettingPreset.course.id")
    @Transactional // Updating/Creating query
    @Override
    <S extends UserCourseNotificationSettingPreset> S save(S userCourseNotificationSettingPreset);

    /***
     * Deleting will clear the user's cached settings.
     *
     * @param userCourseNotificationSettingPreset to delete
     */
    @CacheEvict(key = "'setting_preset_' + #userCourseNotificationSettingPreset.user.id + '_' + #userCourseNotificationSettingPreset.course.id")
    @Transactional // Deleting Query
    @Override
    void delete(UserCourseNotificationSettingPreset userCourseNotificationSettingPreset);
}
