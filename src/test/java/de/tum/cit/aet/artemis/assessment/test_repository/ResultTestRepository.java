package de.tum.cit.aet.artemis.assessment.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;

@Lazy
@Repository
@Primary
public interface ResultTestRepository extends ResultRepository {

    @EntityGraph(type = LOAD, attributePaths = "submission")
    Optional<Result> findResultWithSubmissionsById(long resultId);

    Set<Result> findAllBySubmissionParticipationExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "feedbacks" })
    Set<Result> findAllWithEagerFeedbackByAssessorIsNotNullAndSubmission_Participation_ExerciseIdAndCompletionDateIsNotNull(long exerciseId);

    Optional<Result> findDistinctBySubmissionId(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = "feedbacks")
    Optional<Result> findDistinctWithFeedbackBySubmissionId(long submissionId);

    List<Result> findBySubmissionParticipationIdOrderByCompletionDateDesc(long participationId);

    default Result findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDescElseThrow(long participationId) {
        return getValueElseThrow(findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(participationId));
    }

    /**
     * Finds the first result by participation ID, including its submissions, ordered by completion date in descending order.
     * This method avoids in-memory paging by retrieving the first result directly from the database.
     *
     * @param participationId the ID of the participation to find the result for
     * @return an {@code Optional} containing the first {@code Result} with submissions, ordered by completion date in descending order,
     *         or an empty {@code Optional} if no result is found
     */
    default Optional<Result> findFirstWithSubmissionsByParticipationIdOrderByCompletionDateDesc(long participationId) {
        var resultOptional = findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(participationId);
        if (resultOptional.isEmpty()) {
            return Optional.empty();
        }
        var id = resultOptional.get().getId();
        return findResultWithSubmissionsById(id);
    }

    // Result-scoped cleanup queries used by AssessmentUploadResultTestService. Each deletes rows that reference a result by its id; they are collected on this repository, rather
    // than spread over entity-specific test repositories, so all result cleanup runs through one place.

    /**
     * Finds the manual results (MANUAL or SEMI_AUTOMATIC) of the given participations in one exercise.
     *
     * @param exerciseId       target exercise id
     * @param participationIds participations to look up
     * @return ids of the matching manual results
     */
    @Query("""
            SELECT r.id
            FROM Result r
            WHERE r.exerciseId = :exerciseId
                AND r.assessmentType IN (de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL, de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC)
                AND r.submission.participation.id IN :participationIds
            """)
    List<Long> findManualResultIds(@Param("exerciseId") long exerciseId, @Param("participationIds") Collection<Long> participationIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM Result r WHERE r.id IN :resultIds")
    void deleteResultsByIds(@Param("resultIds") Collection<Long> resultIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM Feedback feedback WHERE feedback.result.id IN :resultIds")
    void deleteFeedbackByResultIds(@Param("resultIds") Collection<Long> resultIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM AssessmentNote note WHERE note.resultId IN :resultIds")
    void deleteAssessmentNotesByResultIds(@Param("resultIds") Collection<Long> resultIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM LongFeedbackText longFeedback WHERE longFeedback.feedback.result.id IN :resultIds")
    void deleteLongFeedbackTextByResultIds(@Param("resultIds") Collection<Long> resultIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM Complaint c WHERE c.result.id IN :resultIds")
    void deleteComplaintsByResultIds(@Param("resultIds") Collection<Long> resultIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM ComplaintResponse cr WHERE cr.complaint.id IN (SELECT c.id FROM Complaint c WHERE c.result.id IN :resultIds)")
    void deleteComplaintResponsesByResultIds(@Param("resultIds") Collection<Long> resultIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM Rating rating WHERE rating.result.id IN :resultIds")
    void deleteRatingsByResultIds(@Param("resultIds") Collection<Long> resultIds);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE ParticipantScore p
            SET p.lastResult = NULL, p.lastPoints = NULL, p.lastScore = NULL
            WHERE p.lastResult.id IN :resultIds
            """)
    void clearParticipantScoreLastResultByResultIds(@Param("resultIds") Collection<Long> resultIds);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE ParticipantScore p
            SET p.lastRatedResult = NULL, p.lastRatedPoints = NULL, p.lastRatedScore = NULL
            WHERE p.lastRatedResult.id IN :resultIds
            """)
    void clearParticipantScoreLastRatedResultByResultIds(@Param("resultIds") Collection<Long> resultIds);
}
