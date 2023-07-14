package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LtiOutcomeUrl;
import de.tum.in.www1.artemis.domain.User;

/**
 * Spring Data JPA repository for the LtiOutcomeUrl entity.
 */
public interface LtiOutcomeUrlRepository extends JpaRepository<LtiOutcomeUrl, Long> {

    Optional<LtiOutcomeUrl> findByUserAndExercise(User user, Exercise exercise);
}
