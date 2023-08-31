package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;

/**
 * Spring Data repository for the SubmissionPolicy entity.
 */
@Repository
public interface SubmissionPolicyRepository extends JpaRepository<SubmissionPolicy, Long> {

    SubmissionPolicy findByProgrammingExerciseId(Long exerciseId);

    @Query("""
                SELECT s
                FROM SubmissionPolicy s
                WHERE s.programmingExercise.id IN :exerciseIds
            """)
    Set<SubmissionPolicy> findAllByProgrammingExerciseIds(@Param("exerciseIds") Collection<Long> exerciseIds);
}
