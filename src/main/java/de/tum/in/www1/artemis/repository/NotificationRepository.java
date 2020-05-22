package de.tum.in.www1.artemis.repository;

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

    @Query("SELECT notification FROM Notification notification WHERE notification.id "
            + "IN(SELECT singleUserNotification FROM SingleUserNotification singleUserNotification WHERE singleUserNotification.recipient.login = :#{#login}) "
            + "OR notification.id IN(SELECT groupNotification FROM GroupNotification groupNotification WHERE "
            + "(groupNotification.course.instructorGroupName IN :#{#currentUserGroups} AND groupNotification.type = 'INSTRUCTOR') "
            + "OR (groupNotification.course.teachingAssistantGroupName IN :#{#currentUserGroups} AND groupNotification.type = 'TA') "
            + "OR (groupNotification.course.studentGroupName IN :#{#currentUserGroups} AND groupNotification.type = 'STUDENT'))")
    Page<Notification> findAllNotificationsForRecipientWithLogin(@Param("currentUserGroups") Set<String> currentUserGroups, @Param("login") String login, Pageable pageable);
}
