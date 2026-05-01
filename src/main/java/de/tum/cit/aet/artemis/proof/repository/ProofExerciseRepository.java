package de.tum.cit.aet.artemis.proof.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.proof.domain.ProofExercise;

/**
 * Spring Data JPA repository for the ProofExercise entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ProofExerciseRepository extends JpaRepository<ProofExercise, Long> {

    List<ProofExercise> findByCourseId(Long courseId);
}
