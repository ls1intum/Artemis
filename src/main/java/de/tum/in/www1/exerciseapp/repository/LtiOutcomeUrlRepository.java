package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.domain.LtiOutcomeUrl;

import de.tum.in.www1.exerciseapp.domain.User;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the LtiOutcomeUrl entity.
 */
@SuppressWarnings("unused")
public interface LtiOutcomeUrlRepository extends JpaRepository<LtiOutcomeUrl,Long> {

    @Query("select ltiOutcomeUrl from LtiOutcomeUrl ltiOutcomeUrl where ltiOutcomeUrl.user.login = ?#{principal}")
    List<LtiOutcomeUrl> findByUserIsCurrentUser();

    @Query("select ltiOutcomeUrl from LtiOutcomeUrl ltiOutcomeUrl where ltiOutcomeUrl.user.login = ?#{principal} and ltiOutcomeUrl.exercise.id = :#{#exercise.id}")
    Optional<LtiOutcomeUrl> findByUserIsCurrentUserAndExercise(@Param("exercise") Exercise exercise);


    Optional<LtiOutcomeUrl> findByUserAndExercise(User user, Exercise exercise);


}
