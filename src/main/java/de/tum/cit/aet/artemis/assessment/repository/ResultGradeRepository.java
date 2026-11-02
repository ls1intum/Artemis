package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/** Read operations for publishing completed grades, independently of pending result state used by the UI. */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ResultGradeRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * Loads the latest result relevant to grade publication, including its submission and feedback.
     *
     * @param participationId the participation whose grade is published
     * @return the latest grade-relevant result, if present
     */
    default Optional<Result> findLatestGradeRelevantResultWithCompletionDate(long participationId) {
        var ids = findGradeRelevantResultIdsByParticipationId(participationId, PageRequest.of(0, 1));
        return ids.isEmpty() ? Optional.empty() : findResultWithSubmissionAndFeedbacksById(ids.getFirst());
    }

    /**
     * Selects grade-relevant result ids before loading their collection graph, so pagination stays in the database.
     *
     * @param participationId the participation whose results are considered
     * @param pageable        the requested page, with size one for latest-grade publication
     * @return eligible result ids ordered by completion date and id, newest first
     */
    @Query("""
            SELECT r.id
            FROM Result r
            WHERE r.submission.participation.id = :participationId
                AND r.score IS NOT NULL
                AND r.completionDate IS NOT NULL
                AND (r.assessmentType IS NULL OR r.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC_ATHENA OR r.successful = TRUE)
            ORDER BY r.completionDate DESC, r.id DESC
            """)
    List<Long> findGradeRelevantResultIdsByParticipationId(@Param("participationId") long participationId, Pageable pageable);

    /**
     * Loads the selected grade with the graph needed for publication.
     *
     * @param resultId the selected result id
     * @return the result with submission and feedback, if it still exists
     */
    @EntityGraph(type = LOAD, attributePaths = { "feedbacks", "submission" })
    Optional<Result> findResultWithSubmissionAndFeedbacksById(long resultId);
}
