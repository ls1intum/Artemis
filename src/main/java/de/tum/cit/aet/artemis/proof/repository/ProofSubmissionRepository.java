package de.tum.cit.aet.artemis.proof.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;

/**
 * Spring Data JPA repository for the ProofSubmission entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ProofSubmissionRepository extends JpaRepository<ProofSubmission, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "results", "participation.exercise" })
    Optional<ProofSubmission> findWithEagerParticipationExerciseResultsById(Long submissionId);
}
