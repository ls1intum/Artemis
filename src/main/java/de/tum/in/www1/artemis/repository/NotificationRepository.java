package de.tum.in.www1.artemis.repository;

import java.util.List;

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

    @Query("select notification from Notification notification where notification.author.login = ?#{principal.username}")
    List<Notification> findByAuthorIsCurrentUser();

    @Query("select notification from Notification notification left join notification.course left join notification.recipient "
            + "where (notification.recipient is null and ((notification.course.instructorGroupName in :#{#currentGroups} AND notification.type = 'INSTRUCTOR') "
            + "or (notification.course.teachingAssistantGroupName in :#{#currentGroups} AND notification.type = 'TA') "
            + "or (notification.course.studentGroupName in :#{#currentGroups} AND notification.type = 'STUDENT')))"
            + "or notification.course is null and notification.recipient.login = ?#{principal.username}")
    Page<Notification> findAllNotificationsForCurrentUser(@Param("currentGroups") List<String> currentUserGroups, Pageable pageable);
}
