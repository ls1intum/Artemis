package de.tum.cit.aet.artemis.communication.test_repository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationStatusRepository;

@Repository
@Primary
public interface UserCourseNotificationStatusTestRepository extends UserCourseNotificationStatusRepository {

    UserCourseNotificationStatus findByCourseNotificationId(Long courseNotification_id);
}
