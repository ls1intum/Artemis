package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;

/**
 * Spring Data JPA repository for the QuizSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

    @Query("""
            SELECT DISTINCT submission FROM QuizSubmission submission
            LEFT JOIN FETCH submission.submittedAnswers
            LEFT JOIN FETCH submission.results r
            LEFT JOIN FETCH r.feedbacks
            LEFT JOIN FETCH r.assessor
            WHERE submission.id = :#{#submissionId}
            """)
    Optional<QuizSubmission> findWithEagerResultAndFeedbackById(@Param("submissionId") long submissionId);

    @Query("""
            SELECT DISTINCT submission FROM QuizSubmission submission
            LEFT JOIN FETCH submission.submittedAnswers
            WHERE submission.id = :#{#submissionId}
            """)
    QuizSubmission findByIdWithEagerSubmittedAnswers(@Param("submissionId") long submissionId);
}
