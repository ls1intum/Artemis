package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ModelingSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


/**
 * Spring Data JPA repository for the ModelingSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingSubmissionRepository extends JpaRepository<ModelingSubmission, Long> {

    @Query("select distinct submission from Submission submission left join fetch submission.result r left join fetch r.assessor where submission.id = :#{#submissionId}")
    Optional<ModelingSubmission> findByIdWithEagerResult(@Param("submissionId") Long submissionId);

    @Query("select distinct submission from Submission submission left join fetch submission.result r left join fetch r.feedbacks where submission.id = :#{#submissionId}")
    Optional<ModelingSubmission> findByIdWithEagerResultAndFeedback(@Param("submissionId") Long submissionId);
}
