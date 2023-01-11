package de.tum.in.www1.artemis.repository;

import java.time.ZonedDateTime;
import java.util.Set;

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
            SELECT notification FROM Notification notification LEFT JOIN notification.course LEFT JOIN notification.recipient
            WHERE notification.notificationDate IS NOT NULL
                AND (cast(:hideUntil as timestamp ) IS NULL OR notification.notificationDate > :hideUntil)
                AND (
                    (type(notification) = GroupNotification
                        AND ((notification.course.instructorGroupName IN :#{#currentGroups} AND notification.type = 'INSTRUCTOR')
                            OR (notification.course.teachingAssistantGroupName IN :#{#currentGroups} AND notification.type = 'TA')
                            OR (notification.course.editorGroupName IN :#{#currentGroups} AND notification.type = 'EDITOR')
                            OR (notification.course.studentGroupName IN :#{#currentGroups} AND notification.type = 'STUDENT')
                        )
                    )
                    OR type(notification) = SingleUserNotification and notification.recipient.login = :#{#login}
                )
            """)
    Page<Notification> findAllNotificationsForRecipientWithLogin(@Param("currentGroups") Set<String> currentUserGroups, @Param("login") String login,
            @Param("hideUntil") ZonedDateTime hideUntil, Pageable pageable);

    @Query("""
            SELECT notification FROM Notification notification LEFT JOIN notification.course LEFT JOIN notification.recipient
            WHERE notification.notificationDate IS NOT NULL
                AND (:#{#hideUntil} IS NULL OR notification.notificationDate > :#{#hideUntil})
                AND (
                     (type(notification) = GroupNotification
                        AND (notification.title NOT IN :#{#deactivatedTitles}
                            OR notification.title IS NULL
                        )
                        AND ((notification.course.instructorGroupName IN :#{#currentGroups} AND notification.type = 'INSTRUCTOR')
                           OR (notification.course.teachingAssistantGroupName IN :#{#currentGroups} AND notification.type = 'TA')
                           OR (notification.course.editorGroupName IN :#{#currentGroups} AND notification.type = 'EDITOR')
                           OR (notification.course.studentGroupName IN :#{#currentGroups} AND notification.type = 'STUDENT'))
                     )
                     OR (type(notification) = SingleUserNotification
                        AND notification.recipient.login = :#{#login}
                        AND (notification.title NOT IN :#{#deactivatedTitles}
                            OR notification.title IS NULL
                        )
                     )
                )
            """)
    Page<Notification> findAllNotificationsFilteredBySettingsForRecipientWithLogin(@Param("currentGroups") Set<String> currentUserGroups, @Param("login") String login,
            @Param("hideUntil") ZonedDateTime hideUntil, @Param("deactivatedTitles") Set<String> deactivatedTitles, Pageable pageable);
}
