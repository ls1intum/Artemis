package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;

/**
 * Spring Data JPA repository for the OnlineCourseConfiguration entity.
 */
@SuppressWarnings("unused")
@Repository
public interface OnlineCourseConfigurationRepository extends JpaRepository<OnlineCourseConfiguration, Long> {

    Optional<OnlineCourseConfiguration> findByRegistrationId(String registrationId);

}
