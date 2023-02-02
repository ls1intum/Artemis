package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the FileUploadSubmission entity.
 */
@Repository
public interface FileUploadSubmissionRepository extends JpaRepository<FileUploadSubmission, Long> {

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("select distinct submission from FileUploadSubmission submission left join fetch submission.results r left join fetch r.feedbacks left join fetch r.assessor where submission.id = :#{#submissionId}")
    Optional<FileUploadSubmission> findByIdWithEagerResultAndAssessorAndFeedback(@Param("submissionId") Long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its assessor
     */
    @Query("select distinct submission from FileUploadSubmission submission left join fetch submission.results r left join fetch r.assessor where submission.id = :#{#submissionId}")
    Optional<FileUploadSubmission> findByIdWithEagerResult(@Param("submissionId") Long submissionId);

    /**
     * Load the file upload submission with the given id together with its result, the feedback list of the result, the assessor of the result, its participation and all results of
     * the participation.
     *
     * @param submissionId the id of the file upload submission that should be loaded from the database
     * @return the file upload submission with its result, the feedback list of the result, the assessor of the result, its participation and all results of the participation
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.assessor", "participation", "participation.results" })
    Optional<FileUploadSubmission> findWithResultsFeedbacksAssessorAndParticipationResultsById(Long submissionId);

    /**
     * Get the file upload submission with the given id from the database. The submission is loaded together with its result, the feedback of the result and the assessor of the
     * result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the file upload submission with the given id
     */
    @NotNull
    default FileUploadSubmission findByIdWithEagerResultAndAssessorAndFeedbackElseThrow(Long submissionId) {
        return findByIdWithEagerResultAndAssessorAndFeedback(submissionId).orElseThrow(() -> new EntityNotFoundException("File Upload Submission", submissionId));
    }

    /**
     * Get the file upload submission with the given id from the database. The submission is loaded together with its result, the feedback of the result, the assessor of the result,
     * its participation and all results of the participation. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the file upload submission with the given id
     */
    @NotNull
    default FileUploadSubmission findByIdWithEagerResultAndFeedbackAndAssessorAndParticipationResultsElseThrow(Long submissionId) {
        return findWithResultsFeedbacksAssessorAndParticipationResultsById(submissionId).orElseThrow(() -> new EntityNotFoundException("File Upload Submission", submissionId));
    }

    /**
     * Get the file upload submission with the given id from the database. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the file upload submission with the given id
     */
    @NotNull
    default FileUploadSubmission findByIdElseThrow(Long submissionId) {
        return findById(submissionId).orElseThrow(() -> new EntityNotFoundException("File Upload Submission", submissionId));
    }
}
