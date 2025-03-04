package de.tum.cit.aet.artemis.course_notification.test_repository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.course_notification.repository.CourseNotificationParameterRepository;

@Repository
@Primary
public interface CourseNotificationParameterTestRepository extends CourseNotificationParameterRepository {
}
