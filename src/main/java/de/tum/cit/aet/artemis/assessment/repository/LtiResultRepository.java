package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Loads official results for LTI grade exports, excluding preliminary Athena results.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface LtiResultRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * Loads the latest non-Athena result with submission and feedback for official LTI grade export.
     *
     * @param participationId the participation whose grade is exported
     * @return the latest eligible result, or empty when only preliminary results exist
     */
    default Optional<Result> findLatestNonAthenaResultWithSubmissionAndFeedbacks(long participationId) {
        return findNonAthenaResultsByParticipationIdOrderByCompletionDateDesc(participationId, Pageable.ofSize(1)).stream().findFirst()
                .flatMap(result -> findResultWithSubmissionAndFeedbacksById(result.getId()));
    }

    @Query("""
            SELECT r FROM Result r
            WHERE r.submission.participation.id = :participationId
                AND (r.assessmentType IS NULL OR r.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC_ATHENA)
            ORDER BY r.completionDate DESC
            """)
    List<Result> findNonAthenaResultsByParticipationIdOrderByCompletionDateDesc(@Param("participationId") long participationId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "feedbacks", "submission" })
    Optional<Result> findResultWithSubmissionAndFeedbacksById(long resultId);
}
