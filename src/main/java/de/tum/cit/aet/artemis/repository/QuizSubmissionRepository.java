package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.quiz.QuizSubmission;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the QuizSubmission entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface QuizSubmissionRepository extends ArtemisJpaRepository<QuizSubmission, Long> {

    @Query("""
            SELECT DISTINCT submission
            FROM QuizSubmission submission
                LEFT JOIN FETCH submission.submittedAnswers
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH r.assessor
            WHERE submission.id = :submissionId
            """)
    Optional<QuizSubmission> findWithEagerResultAndFeedbackById(@Param("submissionId") long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "submittedAnswers" })
    QuizSubmission findWithEagerSubmittedAnswersById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "submittedAnswers" })
    Optional<QuizSubmission> findWithEagerSubmittedAnswersByParticipationId(long participationId);

    Set<QuizSubmission> findByParticipation_Exercise_Id(long exerciseId);

    @Query("""
            SELECT submission
            FROM QuizSubmission submission
                JOIN submission.participation participation
                JOIN participation.exercise exercise
            WHERE exercise.id = :quizExerciseId
            """)
    Optional<QuizSubmission> findByQuizExerciseId(@Param("quizExerciseId") long quizExerciseId);

    /**
     * Retrieve QuizSubmission for given quiz batch and studentLogin
     *
     * @param quizBatchId  the id of the quiz batch for which QuizSubmission is to be retrieved
     * @param studentLogin the login of the student for which QuizSubmission is to be retrieved
     * @return QuizSubmission for given quiz batch and studentLogin
     */
    @Query("""
            SELECT submission
            FROM QuizSubmission submission
                JOIN QuizBatch quizBatch ON submission.quizBatch = quizBatch.id
                JOIN TREAT(submission.participation AS StudentParticipation) participation
            WHERE quizBatch.id = :quizBatchId
                AND participation.student.login = :studentLogin
            """)
    Set<QuizSubmission> findAllByQuizBatchAndStudentLogin(@Param("quizBatchId") Long quizBatchId, @Param("studentLogin") String studentLogin);

    @Query("""
            SELECT submission
            FROM QuizSubmission submission
                LEFT JOIN TREAT(submission.participation AS StudentParticipation) participation
            WHERE participation.exercise.id = :exerciseId
                AND participation.student.login = :studentLogin
            """)
    Optional<QuizSubmission> findByExerciseIdAndStudentLogin(@Param("exerciseId") Long exerciseId, @Param("studentLogin") String studentLogin);
}
