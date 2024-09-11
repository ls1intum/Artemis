package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.notification.Notification;

/**
 * Spring Data repository for the Notification entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface NotificationRepository extends ArtemisJpaRepository<Notification, Long> {

    @Query("""
            SELECT notification
            FROM Notification notification
                LEFT JOIN TREAT(notification AS GroupNotification).course course
                LEFT JOIN TREAT(notification AS SingleUserNotification).recipient recipient
            WHERE notification.notificationDate > :hideUntil
                AND (
                    (TYPE(notification) = GroupNotification
                        AND ((
                                course.instructorGroupName IN :currentGroups
                                AND TREAT(notification AS GroupNotification).type = de.tum.cit.aet.artemis.domain.enumeration.GroupNotificationType.INSTRUCTOR
                            ) OR (
                                course.teachingAssistantGroupName IN :currentGroups
                                AND TREAT(notification AS GroupNotification).type = de.tum.cit.aet.artemis.domain.enumeration.GroupNotificationType.TA
                            ) OR (
                                course.editorGroupName IN :currentGroups
                                AND TREAT(notification AS GroupNotification).type = de.tum.cit.aet.artemis.domain.enumeration.GroupNotificationType.EDITOR
                            ) OR (
                                course.studentGroupName IN :currentGroups
                                AND TREAT(notification AS GroupNotification).type = de.tum.cit.aet.artemis.domain.enumeration.GroupNotificationType.STUDENT
                            )
                        )
                    ) OR (TYPE(notification) = SingleUserNotification
                        AND recipient.login = :login
                        AND (notification.title NOT IN :titlesToNotLoadNotification OR notification.title IS NULL)
                    ) OR (
                        TYPE(notification) = TutorialGroupNotification
                        AND TREAT(notification AS TutorialGroupNotification).tutorialGroup.id IN :tutorialGroupIds
                    )
                )
            """)
    // For some reason IntelliJ doesn't parse this JPQL, shows an error and the syntax highlighting is broken in the WHERE clause. However, it compiles and works fine.
    Page<Notification> findAllNotificationsForRecipientWithLogin(@Param("currentGroups") Set<String> currentGroups, @Param("login") String login,
            @NotNull @Param("hideUntil") ZonedDateTime hideUntil, @Param("tutorialGroupIds") Set<Long> tutorialGroupIds,
            @Param("titlesToNotLoadNotification") Set<String> titlesToNotLoadNotification, Pageable pageable);

    @Query("""
            SELECT notification
            FROM Notification notification
                LEFT JOIN TREAT(notification AS GroupNotification).course course
                LEFT JOIN TREAT(notification AS SingleUserNotification).recipient recipient
            WHERE notification.notificationDate > :hideUntil
                AND (
                    (TYPE(notification) = GroupNotification
                        AND (notification.title NOT IN :deactivatedTitles OR notification.title IS NULL)
                        AND ((
                                course.instructorGroupName IN :currentGroups
                                AND TREAT(notification AS GroupNotification).type = de.tum.cit.aet.artemis.domain.enumeration.GroupNotificationType.INSTRUCTOR
                            ) OR (
                                course.teachingAssistantGroupName IN :currentGroups
                                AND TREAT(notification AS GroupNotification).type = de.tum.cit.aet.artemis.domain.enumeration.GroupNotificationType.TA
                            ) OR (
                                course.editorGroupName IN :currentGroups
                                AND TREAT(notification AS GroupNotification).type = de.tum.cit.aet.artemis.domain.enumeration.GroupNotificationType.EDITOR
                            ) OR (
                                course.studentGroupName IN :currentGroups
                                AND TREAT(notification AS GroupNotification).type = de.tum.cit.aet.artemis.domain.enumeration.GroupNotificationType.STUDENT
                            )
                        )
                    ) OR (TYPE(notification) = SingleUserNotification
                        AND recipient.login = :login
                        AND (notification.title NOT IN :titlesToNotLoadNotification
                            OR notification.title IS NULL
                        )
                        AND (notification.title NOT IN :deactivatedTitles
                            OR notification.title IS NULL
                        )
                    ) OR (TYPE(notification) = TutorialGroupNotification
                        AND TREAT(notification AS TutorialGroupNotification).tutorialGroup.id IN :tutorialGroupIds
                        AND (notification.title NOT IN :deactivatedTitles
                            OR notification.title IS NULL
                        )
                    )
                )
            """)
    // For some reason IntelliJ doesn't parse this JPQL, shows an error and the syntax highlighting is broken in the WHERE clause. However, it compiles and works fine.
    Page<Notification> findAllNotificationsFilteredBySettingsForRecipientWithLogin(@Param("currentGroups") Set<String> currentGroups, @Param("login") String login,
            @NotNull @Param("hideUntil") ZonedDateTime hideUntil, @Param("deactivatedTitles") Set<String> deactivatedTitles, @Param("tutorialGroupIds") Set<Long> tutorialGroupIds,
            @Param("titlesToNotLoadNotification") Set<String> titlesToNotLoadNotification, Pageable pageable);
}
