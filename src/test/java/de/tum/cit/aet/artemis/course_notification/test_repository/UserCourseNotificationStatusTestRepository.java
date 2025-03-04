package de.tum.cit.aet.artemis.course_notification.test_repository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.course_notification.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.course_notification.repository.UserCourseNotificationStatusRepository;

@Repository
@Primary
public interface UserCourseNotificationStatusTestRepository extends UserCourseNotificationStatusRepository {

    UserCourseNotificationStatus findByCourseNotificationId(Long courseNotification_id);
}
