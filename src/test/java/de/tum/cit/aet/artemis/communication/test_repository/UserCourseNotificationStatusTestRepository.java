package de.tum.cit.aet.artemis.communication.test_repository;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationStatusRepository;

@Repository
@Primary
public interface UserCourseNotificationStatusTestRepository extends UserCourseNotificationStatusRepository {

    UserCourseNotificationStatus findByCourseNotificationId(Long courseNotificationId);

    List<UserCourseNotificationStatus> findAllByCourseNotificationId(Long courseNotificationId);

    /**
     * Checks if a notification was sent only to the specified user.
     *
     * @param courseNotificationId the ID of the course notification
     * @param userId               the ID of the user who should have received the notification
     * @return true if only the specified user received the notification, false otherwise
     */
    default boolean wasNotificationSentOnlyToUser(Long courseNotificationId, Long userId) {
        List<UserCourseNotificationStatus> statuses = findAllByCourseNotificationId(courseNotificationId);
        return statuses.size() == 1 && statuses.getFirst().getUser().getId().equals(userId);
    }
}
