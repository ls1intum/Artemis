package de.tum.cit.aet.artemis.lti.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.lti.repository.OnlineCourseConfigurationRepository;

/**
 * Spring Data JPA repository for the OnlineCourseConfiguration entity.
 */
@Lazy
@Repository
@Primary
public interface OnlineCourseConfigurationTestRepository extends OnlineCourseConfigurationRepository {
    // This interface is intentionally left blank. Spring Data JPA generates the implementation at runtime.
}
