package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * Generic interface for Spring Data JPA repositories for file upload, modeling and text submissions.
 */
@NoRepositoryBean
public interface GenericSubmissionRepository<T extends Submission> extends JpaRepository<T, Long> {

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("select distinct submission from #{#entityName} submission left join fetch submission.result r left join fetch r.feedbacks left join fetch r.assessor where submission.id=?1")
    Optional<T> findByIdWithEagerResultAndFeedback(@Param("submissionId") Long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its assessor
     */
    @Query("select distinct submission from #{#entityName} submission left join fetch submission.result r left join fetch r.assessor where submission.id=?1")
    Optional<T> findByIdWithEagerResultAndAssessor(@Param("submissionId") Long submissionId);

    /**
     * Load the submission with the given id together with its result, the feedback list of the result, the assessor of the result, its participation and all results of
     * the participation.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the submission with its result, the feedback list of the result, the assessor of the result, its participation and all results of the participation
     */
    @EntityGraph(attributePaths = { "result", "result.feedbacks", "result.assessor", "participation", "participation.results" })
    Optional<T> findWithEagerResultAndFeedbackAndAssessorAndParticipationResultsById(Long submissionId);
}
