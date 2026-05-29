package de.tum.cit.aet.artemis.notification.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.notification.repository.CourseNotificationRepository;

@Lazy
@Repository
@Primary
public interface CourseNotificationTestRepository extends CourseNotificationRepository {
}
