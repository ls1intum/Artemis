package de.tum.cit.aet.artemis.coursenotification.test_repository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.coursenotification.repository.CourseNotificationParameterRepository;

@Repository
@Primary
public interface CourseNotificationParameterTestRepository extends CourseNotificationParameterRepository {
}
