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

    /**
     * Loads submitted answers from the database in case there is a QuizSubmission in one of the passed student participation
     * Assumes that submissions are loaded eagerly in case they exist
     * @param participations the student participations for which the submitted answers in quiz submissions should be loaded
     */
    default void loadQuizSubmissionsSubmittedAnswers(Collection<StudentParticipation> participations) {
        for (var participation : participations) {
            if (participation.getExercise() instanceof QuizExercise) {
                if (participation.getSubmissions() != null) {
                    for (var submission : participation.getSubmissions()) {
                        var quizSubmission = (QuizSubmission) submission;
                        // submitted answers can only be lazy loaded in many cases, so we load them explicitly for each submission here
                        var submittedAnswers = findBySubmission(quizSubmission);
                        quizSubmission.setSubmittedAnswers(submittedAnswers);
                    }
                }
            }
        }
    }
}
