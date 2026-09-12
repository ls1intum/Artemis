package de.tum.cit.aet.artemis.notification.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSettingSpecificationDTO;

/**
 * Repository for the {@link UserCourseNotificationSettingSpecification} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
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
     * @return The channels the user has enabled per notification type.
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSettingSpecificationDTO(
                s.courseNotificationType, s.email, s.push, s.webapp)
            FROM UserCourseNotificationSettingSpecification s
            WHERE s.user.id = :userId
                AND s.course.id = :courseId
            """)
    List<UserCourseNotificationSettingSpecificationDTO> findAllByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /***
     * Get the setting specification entities for a given user id and course id, for a caller that has to write them.
     * <p>
     * Deliberately not cached: the cached read above answers with the channel flags alone, so that the store never
     * holds an entity. A caller that intends to delete these rows needs their identity, and reads them from the
     * database.
     *
     * @param userId   to query for
     * @param courseId to query for
     *
     * @return The list of user setting specification entities.
     */
    List<UserCourseNotificationSettingSpecification> findAllEntitiesByUserIdAndCourseId(Long userId, Long courseId);

    /***
     * Saving will clear the user's cached settings.
     *
     * @param userCourseNotificationSettingSpecification to store
     *
     * @return Newly stored {@link UserCourseNotificationSettingSpecification}
     */
    @Transactional // OK because of modifying query
    @Modifying
    @Override
    <S extends UserCourseNotificationSettingSpecification> S save(S userCourseNotificationSettingSpecification);

    /***
     * Deleting will clear the user's cached settings.
     *
     * @param userCourseNotificationSettingSpecification to delete
     */
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
    @Transactional // OK because of delete
    @Modifying
    void deleteAllByCourseId(long courseId);
}
