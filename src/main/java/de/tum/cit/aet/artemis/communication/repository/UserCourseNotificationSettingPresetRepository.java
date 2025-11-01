package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Repository for the {@link UserCourseNotificationSettingPreset} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
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

    /**
     * Find all course notification setting presets by user id.
     *
     * @param userId id to query for
     * @return list of course notification setting presets for the user
     */
    List<UserCourseNotificationSettingPreset> findAllByUserId(long userId);

    // NOTE: We must clear all entries because we don't know which users had a preset for the course
    @Transactional // ok because of delete
    @Modifying
    @CacheEvict(allEntries = true)
    void deleteAllByCourseId(long courseId);
}
