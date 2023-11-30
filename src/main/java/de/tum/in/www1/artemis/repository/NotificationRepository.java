package de.tum.in.www1.artemis.repository;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.Notification;

/**
 * Spring Data repository for the Notification entity.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
                    SELECT notification
                    FROM Notification notification
                        LEFT JOIN TREAT(notification as GroupNotification).course
                        LEFT JOIN TREAT(notification as SingleUserNotification).recipient
                    WHERE notification.notificationDate > :hideUntil
                        AND (
                            (TREAT(notification AS GroupNotification) IS NOT NULL
                                AND ((TREAT(notification AS GroupNotification).course.instructorGroupName IN :currentGroups AND TREAT(notification AS GroupNotification).type = 'INSTRUCTOR')
                                    OR (TREAT(notification AS GroupNotification).course.teachingAssistantGroupName IN :currentGroups AND TREAT(notification AS GroupNotification).type = 'TA')
                                    OR (TREAT(notification AS GroupNotification).course.editorGroupName IN :currentGroups AND TREAT(notification AS GroupNotification).type = 'EDITOR')
                                    OR (TREAT(notification AS GroupNotification).course.studentGroupName IN :currentGroups AND TREAT(notification AS GroupNotification).type = 'STUDENT')
                                )
                            )
                            OR TREAT(notification AS SingleUserNotification) IS NOT NULL AND TREAT(notification AS SingleUserNotification).recipient.login = :login
                            AND (notification.title NOT IN :titlesToNotLoadNotification
                                OR notification.title IS NULL
                            )
                            OR TREAT(notification AS TutorialGroupNotification) IS NOT NULL AND TREAT(notification AS TutorialGroupNotification).tutorialGroup.id IN :tutorialGroupIds
                         )
            """)
    // For some reason IntelliJ doesn't parse this JPQL, shows an error and the syntax highlighting is broken in the WHERE clause. However, it compiles and works fine.
    Page<Notification> findAllNotificationsForRecipientWithLogin(@Param("currentGroups") Set<String> currentGroups, @Param("login") String login,
            @NotNull @Param("hideUntil") ZonedDateTime hideUntil, @Param("tutorialGroupIds") Set<Long> tutorialGroupIds,
            @Param("titlesToNotLoadNotification") Set<String> titlesToNotLoadNotification, Pageable pageable);

    @Query("""
                    SELECT notification
                    FROM Notification notification
                        LEFT JOIN TREAT(notification as GroupNotification).course
                        LEFT JOIN TREAT(notification as SingleUserNotification).recipient
                    WHERE notification.notificationDate > :hideUntil
                        AND (
                            (TREAT(notification AS GroupNotification) IS NOT NULL
                                AND (notification.title NOT IN :deactivatedTitles
                                    OR notification.title IS NULL
                                )
                               AND ((TREAT(notification AS GroupNotification).course.instructorGroupName IN :currentGroups AND TREAT(notification AS GroupNotification).type = 'INSTRUCTOR')
                                    OR (TREAT(notification AS GroupNotification).course.teachingAssistantGroupName IN :currentGroups AND TREAT(notification AS GroupNotification).type = 'TA')
                                    OR (TREAT(notification AS GroupNotification).course.editorGroupName IN :currentGroups AND TREAT(notification AS GroupNotification).type = 'EDITOR')
                                    OR (TREAT(notification AS GroupNotification).course.studentGroupName IN :currentGroups AND TREAT(notification AS GroupNotification).type = 'STUDENT')
                                )
                         )
                         OR (TREAT(notification AS SingleUserNotification) IS NOT NULL
                            AND TREAT(notification AS SingleUserNotification).recipient.login = :login
                            AND (notification.title NOT IN :titlesToNotLoadNotification
                                OR notification.title IS NULL
                            )
                            AND (notification.title NOT IN :deactivatedTitles
                                OR notification.title IS NULL
                            )
                         )
                         OR (TREAT(notification AS TutorialGroupNotification) IS NOT NULL
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
