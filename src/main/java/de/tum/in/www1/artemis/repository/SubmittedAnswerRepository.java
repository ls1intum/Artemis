package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.SubmittedAnswer;

/**
 * Spring Data JPA repository for the SubmittedAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SubmittedAnswerRepository extends JpaRepository<SubmittedAnswer, Long> {

    Set<SubmittedAnswer> findBySubmission(QuizSubmission quizSubmission);

    default void findQuizSubmissionsSubmittedAnswers(Collection<StudentParticipation> participations) {
        for (var participation : participations) {
            if (participation.getExercise() instanceof QuizExercise) {
                if (participation.getSubmissions() != null && participation.getSubmissions().size() > 0) {
                    var quizSubmission = (QuizSubmission) participation.getSubmissions().iterator().next();
                    var submittedAnswers = findBySubmission(quizSubmission);
                    quizSubmission.setSubmittedAnswers(submittedAnswers);
                }
            }
        }
    }
}
