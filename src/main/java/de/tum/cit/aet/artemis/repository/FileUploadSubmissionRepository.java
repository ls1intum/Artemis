package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the FileUploadSubmission entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface FileUploadSubmissionRepository extends ArtemisJpaRepository<FileUploadSubmission, Long> {

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("""
            SELECT DISTINCT submission
            FROM FileUploadSubmission submission
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH r.assessor
            WHERE submission.id = :submissionId
            """)
    Optional<FileUploadSubmission> findByIdWithEagerResultAndAssessorAndFeedback(@Param("submissionId") long submissionId);

    /**
     * Load the file upload submission with the given id together with its result, the feedback list of the result, the assessor of the result, the assessment note of the result,
     * its participation and all results of the participation.
     *
     * @param submissionId the id of the file upload submission that should be loaded from the database
     * @return the file upload submission with its result, the feedback list of the result, the assessor of the result, its participation and all results of the participation
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.assessor", "results.assessmentNote", "participation", "participation.results" })
    Optional<FileUploadSubmission> findWithResultsFeedbacksAssessorAssessmentNoteAndParticipationResultsById(long submissionId);

    @Query("""
            SELECT submission
            FROM FileUploadSubmission submission
                LEFT JOIN FETCH submission.participation participation
                LEFT JOIN FETCH participation.exercise exercise
                LEFT JOIN FETCH participation.team team
                LEFT JOIN FETCH team.students
            WHERE submission.id = :submissionId
                AND exercise.id = :exerciseId
            """)
    Optional<FileUploadSubmission> findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseId(@Param("submissionId") long submissionId, @Param("exerciseId") long exerciseId);

    /**
     * Get the file upload submission with the given id from the database. The submission is loaded together with its result, the feedback of the result and the assessor of the
     * result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the file upload submission with the given id
     */
    @NotNull
    default FileUploadSubmission findByIdWithEagerResultAndAssessorAndFeedbackElseThrow(long submissionId) {
        return getValueElseThrow(findByIdWithEagerResultAndAssessorAndFeedback(submissionId), submissionId);
    }

    /**
     * Get the file upload submission with the given id from the database. The submission is loaded together with its result, the feedback of the result, the assessor of the
     * result, the assessment note of the result, its participation and all results of the participation. Throws an EntityNotFoundException if no submission could be found for the
     * given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the file upload submission with the given id
     */
    @NotNull
    default FileUploadSubmission findByIdWithEagerResultAndFeedbackAndAssessorAndAssessmentNoteAndParticipationResultsElseThrow(long submissionId) {
        return getValueElseThrow(findWithResultsFeedbacksAssessorAssessmentNoteAndParticipationResultsById(submissionId), submissionId);
    }

    /**
     * Get the file upload submission with the given id from the database. The submission is loaded together with its participation and the team of the participation. Throws an
     * EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @param exerciseId   the id of the exercise that should be loaded from the database
     * @return the file upload submission with the given id
     */
    @NotNull
    default FileUploadSubmission findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseIdElseThrow(long submissionId, long exerciseId) {
        return getValueElseThrow(findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseId(submissionId, exerciseId), submissionId);
    }
}
