package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Notification;

/**
 * Spring Data repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT notification FROM Notification notification LEFT JOIN notification.course LEFT JOIN notification.recipient "
            + "WHERE (notification.recipient IS NULL AND ((notification.course.instructorGroupName IN :#{#currentGroups} AND notification.type = 'INSTRUCTOR') "
            + "OR (notification.course.teachingAssistantGroupName IN :#{#currentGroups} AND notification.type = 'TA') "
            + "OR (notification.course.studentGroupName IN :#{#currentGroups} AND notification.type = 'STUDENT')))"
            + "OR notification.course IS NULL AND notification.recipient.login = :#{#login}")
    Page<Notification> findAllNotificationsForRecipientWithLogin(@Param("currentGroups") Set<String> currentUserGroups, Pageable pageable, @Param("login") String login);
}
