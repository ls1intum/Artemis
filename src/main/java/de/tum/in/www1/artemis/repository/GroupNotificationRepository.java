package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Notification;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for the Notification entity. */
@SuppressWarnings("unused")
@Repository
public interface GroupNotificationRepository extends JpaRepository<Notification, Long> {

    @Query(
            "select groupNotification "
                    + "from GroupNotification groupNotification "
                    + "where (:#{#lastNotificationRead} is null or groupNotification.notificationDate > :#{#lastNotificationRead}) AND "
                    + "((groupNotification.course.instructorGroupName in :#{#currentGroups} AND groupNotification.type = 'INSTRUCTOR') "
                    + "or (groupNotification.course.teachingAssistantGroupName in :#{#currentGroups} AND groupNotification.type = 'TA') "
                    + "or (groupNotification.course.studentGroupName in :#{#currentGroups} AND groupNotification.type = 'STUDENT'))")
    List<Notification> findAllNewNotificationsForCurrentUser(
            @Param("currentGroups") List<String> currentUserGroups,
            @Param("lastNotificationRead") ZonedDateTime lastNotificationRead);
}
