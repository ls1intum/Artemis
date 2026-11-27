package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Finds the latest results of submissions and participations for views that show a finished result. An automatic result
 * whose build is still running, such as the aggregated result of a multi-container build, has no completion date and is
 * skipped, so the previous result stays the latest until the build has finished.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FinishedResultRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * Finds the latest result of the submission with its feedback, skipping automatic results whose build is still running.
     *
     * @param submissionId the id of the submission
     * @return the latest result that is not an unfinished automatic result, if any
     */
    @Query("""
            SELECT r
            FROM Result r
                 LEFT JOIN FETCH r.feedbacks f
            WHERE r.submission.id = :submissionId
            AND r.id = (
                 SELECT MAX(r2.id)
                 FROM Result r2
                 WHERE r2.submission.id = :submissionId
                 AND (r2.assessmentType IS NULL OR r2.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC OR r2.completionDate IS NOT NULL)
               )
            """)
    Optional<Result> findLatestResultWithFeedbacksForSubmission(@Param("submissionId") long submissionId);

    /**
     * Finds the latest result of each of the submissions, skipping automatic results whose build is still running.
     *
     * @param submissionIds the ids of the submissions
     * @return the latest result of each submission that has one that is not an unfinished automatic result
     */
    @Query("""
            SELECT r
            FROM Result r
            WHERE r.submission.id IN :submissionIds
            AND r.id = (
                 SELECT MAX(r2.id)
                 FROM Result r2
                 WHERE r2.submission.id = r.submission.id
                 AND (r2.assessmentType IS NULL OR r2.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC OR r2.completionDate IS NOT NULL)
               )
            """)
    Set<Result> findLatestResultsBySubmissionIds(@Param("submissionIds") Set<Long> submissionIds);

    /**
     * Finds the latest result of each of the participations, skipping automatic results whose build is still running.
     *
     * @param participationIds the ids of the participations
     * @return the latest result of each participation that has one that is not an unfinished automatic result
     */
    @Query("""
            SELECT r
            FROM Result r
            WHERE r.submission.participation.id IN :participationIds
            AND r.id = (
                 SELECT MAX(r2.id)
                 FROM Result r2
                 WHERE r2.submission.participation.id = r.submission.participation.id
                 AND (r2.assessmentType IS NULL OR r2.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC OR r2.completionDate IS NOT NULL)
               )
            """)
    Set<Result> findLatestResultsByParticipationIds(@Param("participationIds") Set<Long> participationIds);
}
