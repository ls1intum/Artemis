package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LtiOutcomeUrl;
import de.tum.in.www1.artemis.domain.User;

/**
 * Spring Data JPA repository for the LtiOutcomeUrl entity.
 */
@SuppressWarnings("unused")
public interface LtiOutcomeUrlRepository extends JpaRepository<LtiOutcomeUrl, Long> {

    @Query("SELECT ltiOutcomeUrl FROM LtiOutcomeUrl ltiOutcomeUrl WHERE ltiOutcomeUrl.user.login = ?#{principal}")
    List<LtiOutcomeUrl> findByUserIsCurrentUser();

    @Query("SELECT ltiOutcomeUrl FROM LtiOutcomeUrl ltiOutcomeUrl WHERE ltiOutcomeUrl.user.login = ?#{principal} AND ltiOutcomeUrl.exercise.id = :#{#exercise.id}")
    Optional<LtiOutcomeUrl> findByUserIsCurrentUserAndExercise(@Param("exercise") Exercise exercise);

    Optional<LtiOutcomeUrl> findByUserAndExercise(User user, Exercise exercise);
}
