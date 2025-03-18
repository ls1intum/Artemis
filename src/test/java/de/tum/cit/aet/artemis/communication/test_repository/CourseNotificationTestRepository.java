package de.tum.cit.aet.artemis.communication.test_repository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.repository.CourseNotificationRepository;

@Repository
@Primary
public interface CourseNotificationTestRepository extends CourseNotificationRepository {
}
