package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the SubmissionPolicy entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SubmissionPolicyRepository extends ArtemisJpaRepository<SubmissionPolicy, Long> {

    SubmissionPolicy findByProgrammingExerciseId(Long exerciseId);

    @Query("""
            SELECT s
            FROM SubmissionPolicy s
            WHERE s.programmingExercise.id IN :exerciseIds
            """)
    Set<SubmissionPolicy> findAllByProgrammingExerciseIds(@Param("exerciseIds") Collection<Long> exerciseIds);
}
