package de.tum.cit.aet.artemis.notification.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSettingPresetEntryDTO;

/**
 * Repository for the {@link UserCourseNotificationSettingPreset} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserCourseNotificationSettingPresetRepository extends ArtemisJpaRepository<UserCourseNotificationSettingPreset, Long> {

    /***
     * Get the selected setting preset of a user in a course. Cached until changed (save or delete is called).
     *
     * @param userId   to query for
     * @param courseId to query for
     *
     * @return The id of the selected preset, or null if the user has no preset for the course.
     */
    @Query("""
            SELECT p.settingPreset
            FROM UserCourseNotificationSettingPreset p
            WHERE p.user.id = :userId
                AND p.course.id = :courseId
            """)
    Short findSettingPresetByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /***
     * Get the selected preset of every given user in a course, in one query.
     * <p>
     * Filtering a notification's recipients asks for this once per recipient per delivery channel, so a course-wide
     * announcement to 3000 students across three channels asked 9000 times. Reading the cohort at once is what makes
     * that bounded; users without a row are simply absent from the result.
     *
     * @param userIds  the users to query for
     * @param courseId to query for
     *
     * @return one entry per user that has a preset for the course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSettingPresetEntryDTO(p.user.id, p.settingPreset)
            FROM UserCourseNotificationSettingPreset p
            WHERE p.user.id IN :userIds
                AND p.course.id = :courseId
            """)
    List<UserCourseNotificationSettingPresetEntryDTO> findSettingPresetsByUserIdsAndCourseId(@Param("userIds") Collection<Long> userIds, @Param("courseId") Long courseId);

    /**
     * Checks whether the user with the given login has chosen a preset in any course that is none of the given ones.
     * <p>
     * The caller passes the presets that do not deliver e-mail by themselves, the custom one, which is described by
     * specifications, and the one that switches everything off. Choosing any other preset is choosing to receive e-mail.
     *
     * @param login           the login of the user
     * @param excludedPresets the presets that do not count
     * @return true if the user chose another preset in at least one course
     */
    @Query("""
            SELECT COUNT(p) > 0
            FROM UserCourseNotificationSettingPreset p
            WHERE p.user.login = :login
                AND p.settingPreset NOT IN :excludedPresets
            """)
    boolean existsChosenPresetByUserLoginOtherThan(@Param("login") String login, @Param("excludedPresets") Collection<Short> excludedPresets);

    /***
     * Get the setting preset entity for a given user id and course id, for a caller that has to write it back.
     * <p>
     * Deliberately not cached: the cached read above answers with the preset value alone, so that the store never holds
     * an entity. A caller that intends to modify the row needs its identity, and reads it from the database.
     *
     * @param userId   to query for
     * @param courseId to query for
     *
     * @return The unique user setting preset entity, or null if the user has none for the course.
     */
    UserCourseNotificationSettingPreset findUserCourseNotificationSettingPresetByUserIdAndCourseId(Long userId, Long courseId);

    /***
     * Saving will clear the user's cached settings.
     *
     * @param userCourseNotificationSettingPreset to store
     *
     * @return Newly stored {@link UserCourseNotificationSettingPreset}
     */
    @Transactional // Updating/Creating query
    @Override
    <S extends UserCourseNotificationSettingPreset> S save(S userCourseNotificationSettingPreset);

    /***
     * Deleting will clear the user's cached settings.
     *
     * @param userCourseNotificationSettingPreset to delete
     */
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
    void deleteAllByCourseId(long courseId);
}
