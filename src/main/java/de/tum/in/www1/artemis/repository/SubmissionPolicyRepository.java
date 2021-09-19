package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for the SubmissionPolicy entity.
 */
@Repository
public interface SubmissionPolicyRepository extends JpaRepository<SubmissionPolicy, Long> {

    SubmissionPolicy findByExerciseId(Long exerciseId);

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"programmingExercise"})
    SubmissionPolicy findByIdWithProgrammingExercise(Long submissionPolicyId);

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"programmingExercise"})
    SubmissionPolicy findByExerciseIdWithProgrammingExercise(Long exerciseId);

}
