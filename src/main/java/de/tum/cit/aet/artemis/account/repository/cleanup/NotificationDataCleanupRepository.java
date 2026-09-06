package de.tum.cit.aet.artemis.account.repository.cleanup;

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
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationSetting;

/**
 * Removes the notification settings and delivery state of a user that is being deleted permanently.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface NotificationDataCleanupRepository extends ArtemisJpaRepository<GlobalNotificationSetting, Long> {

    @Query("""
            SELECT setting.userId AS userId, COUNT(setting) AS count
            FROM GlobalNotificationSetting setting
            WHERE setting.userId IN :userIds
            GROUP BY setting.userId
            """)
    List<UserReferenceCount> countGlobalSettings(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM GlobalNotificationSetting setting
            WHERE setting.userId = :userId
            """)
    int deleteGlobalSettings(@Param("userId") long userId);

    @Query("""
            SELECT device.owner.id AS userId, COUNT(device) AS count
            FROM PushNotificationDeviceConfiguration device
            WHERE device.owner.id IN :userIds
            GROUP BY device.owner.id
            """)
    List<UserReferenceCount> countPushDevices(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM PushNotificationDeviceConfiguration device
            WHERE device.owner.id = :userId
            """)
    int deletePushDevices(@Param("userId") long userId);

    @Query("""
            SELECT preset.user.id AS userId, COUNT(preset) AS count
            FROM UserCourseNotificationSettingPreset preset
            WHERE preset.user.id IN :userIds
            GROUP BY preset.user.id
            """)
    List<UserReferenceCount> countCoursePresets(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserCourseNotificationSettingPreset preset
            WHERE preset.user.id = :userId
            """)
    int deleteCoursePresets(@Param("userId") long userId);

    @Query("""
            SELECT specification.user.id AS userId, COUNT(specification) AS count
            FROM UserCourseNotificationSettingSpecification specification
            WHERE specification.user.id IN :userIds
            GROUP BY specification.user.id
            """)
    List<UserReferenceCount> countCourseSpecifications(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserCourseNotificationSettingSpecification specification
            WHERE specification.user.id = :userId
            """)
    int deleteCourseSpecifications(@Param("userId") long userId);

    @Query("""
            SELECT status.user.id AS userId, COUNT(status) AS count
            FROM UserCourseNotificationStatus status
            WHERE status.user.id IN :userIds
            GROUP BY status.user.id
            """)
    List<UserReferenceCount> countCourseStatuses(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserCourseNotificationStatus status
            WHERE status.user.id = :userId
            """)
    int deleteCourseStatuses(@Param("userId") long userId);
}
