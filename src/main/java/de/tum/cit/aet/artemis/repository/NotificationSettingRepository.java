package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.NotificationSetting;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the NotificationSetting entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface NotificationSettingRepository extends ArtemisJpaRepository<NotificationSetting, Long> {

    @Query("""
            SELECT notificationSetting
            FROM NotificationSetting notificationSetting
                LEFT JOIN FETCH notificationSetting.user user
            WHERE user.id = :userId
            """)
    Set<NotificationSetting> findAllNotificationSettingsForRecipientWithId(@Param("userId") long userId);

    @Query("""
            SELECT notificationSetting
            FROM NotificationSetting notificationSetting
                LEFT JOIN FETCH notificationSetting.user user
            WHERE user.id IN :userIds
            """)
    Set<NotificationSetting> findAllNotificationSettingsForRecipientsWithId(@Param("userIds") List<Long> userIds);

    @Query("""
            SELECT setting
            FROM NotificationSetting setting
                LEFT JOIN FETCH setting.user user
                LEFT JOIN FETCH user.groups
                LEFT JOIN FETCH user.authorities
            WHERE setting.settingId = :settingId
                AND setting.email = TRUE
            """)
    Set<NotificationSetting> findAllNotificationSettingsForUsersWhoEnabledSpecifiedEmailSettingWithEagerGroupsAndAuthorities(@Param("settingId") String settingId);

    /**
     * @param settingId of the notification setting (e.g. NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY)
     * @return all users who enabled the provided email notification setting with eager groups and authorities
     */
    default Set<User> findAllUsersWhoEnabledSpecifiedEmailNotificationSettingWithEagerGroupsAndAuthorities(String settingId) {
        return findAllNotificationSettingsForUsersWhoEnabledSpecifiedEmailSettingWithEagerGroupsAndAuthorities(settingId).stream().map(NotificationSetting::getUser)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves the ids for all muted conversations of the user with the provided id
     *
     * @param userId id of the user
     * @return ids of the conversations the user has muted
     */
    @Query("""
            SELECT cp.conversation.id
            FROM ConversationParticipant cp
            WHERE cp.user.id = :userId AND (cp.isMuted = TRUE OR cp.isHidden = TRUE)
            """)
    Set<Long> findMutedConversations(@Param("userId") long userId);
}
