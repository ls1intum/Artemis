package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;

/**
 * Spring Data JPA repository for the SubmittedAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SubmittedAnswerRepository extends JpaRepository<SubmittedAnswer, Long> {

    Set<SubmittedAnswer> findBySubmission(AbstractQuizSubmission quizSubmission);

    @Query("""
                SELECT sa
                FROM SubmittedAnswer sa
                    LEFT JOIN FETCH sa.submission s
                WHERE s.id IN :submissionIds
            """)
    Set<SubmittedAnswer> findAllBySubmissionIds(@Param("submissionIds") List<Long> submissionIds);

    /**
     * Loads submitted answers from the database in case there is a QuizSubmission in one of the passed student participation
     * Assumes that submissions are loaded eagerly in case they exist
     *
     * @param participations     the student participations for which the submitted answers in quiz submissions should be loaded
     * @param quizExamSubmission
     */
    default void loadQuizSubmissionsSubmittedAnswers(Collection<StudentParticipation> participations, QuizExamSubmission quizExamSubmission) {
        for (var participation : participations) {
            if (participation.getExercise() instanceof QuizExercise) {
                if (participation.getSubmissions() != null) {
                    for (var submission : participation.getSubmissions()) {
                        if (submission instanceof QuizSubmission quizSubmission) {
                            // submitted answers can only be lazy loaded in many cases, so we load them explicitly for each submission here
                            var submittedAnswers = findBySubmission(quizSubmission);
                            quizSubmission.setSubmittedAnswers(submittedAnswers);
                        }
                    }
                }
            }
        }
        if (quizExamSubmission != null) {
            var submittedAnswers = findBySubmission(quizExamSubmission);
            quizExamSubmission.setSubmittedAnswers(submittedAnswers);
        }
    }
}
