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

import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Repository for the {@link UserCourseNotificationSettingSpecification} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
@CacheConfig(cacheNames = "userCourseNotificationSettingSpecification")
public interface UserCourseNotificationSettingSpecificationRepository extends ArtemisJpaRepository<UserCourseNotificationSettingSpecification, Long> {

    /***
     * Get the user setting specification for a given notification type id, user id and course id. Not cached.
     *
     * @param userId           to query for
     * @param courseId         to query for
     * @param notificationType to query for
     *
     * @return The setting specification entity or null if not exists
     */
    List<UserCourseNotificationSettingSpecification> findAllByUserIdAndCourseIdAndCourseNotificationTypeIn(Long userId, Long courseId, List<Short> notificationType);

    /***
     * Get the user setting specifications for a given user id and course id. Cached until changed (save or delete is called).
     *
     * @param userId   to query for
     * @param courseId to query for
     *
     * @return The list of user setting specifications.
     */
    @Cacheable(key = "'setting_specifications_' + #userId + '_' + #courseId")
    List<UserCourseNotificationSettingSpecification> findAllByUserIdAndCourseId(Long userId, Long courseId);

    /***
     * Saving will clear the user's cached settings.
     *
     * @param userCourseNotificationSettingSpecification to store
     *
     * @return Newly stored {@link UserCourseNotificationSettingSpecification}
     */
    @CacheEvict(key = "'setting_specifications_' + #userCourseNotificationSettingSpecification.user.id + '_' + #userCourseNotificationSettingSpecification.course.id")
    @Transactional // OK because of modifying query
    @Modifying
    @Override
    <S extends UserCourseNotificationSettingSpecification> S save(S userCourseNotificationSettingSpecification);

    /***
     * Deleting will clear the user's cached settings.
     *
     * @param userCourseNotificationSettingSpecification to delete
     */
    @CacheEvict(key = "'setting_specifications_' + #userCourseNotificationSettingSpecification.user.id + '_' + #userCourseNotificationSettingSpecification.course.id")
    @Transactional // OK because of delete
    @Modifying
    @Override
    void delete(UserCourseNotificationSettingSpecification userCourseNotificationSettingSpecification);

    /**
     * Find all course notification setting specifications by user id.
     *
     * @param userId id to query for
     * @return list of course notification setting specifications for the user
     */
    List<UserCourseNotificationSettingSpecification> findAllByUserId(long userId);

    // NOTE: we need to clear all cached entries because we don't know which users had a specification for the course
    @CacheEvict(allEntries = true)
    @Transactional // OK because of delete
    @Modifying
    void deleteAllByCourseId(long courseId);
}
