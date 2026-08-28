package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;

/**
 * Spring Data JPA repository for the SubmittedAnswer entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface SubmittedAnswerRepository extends ArtemisJpaRepository<SubmittedAnswer, Long> {

    Set<SubmittedAnswer> findBySubmissionIdIn(Collection<Long> submissionIds);

    /**
     * Loads submitted answers from the database in case there is a QuizSubmission in one of the passed student participation
     * Assumes that submissions are loaded eagerly in case they exist
     *
     * @param participations the student participations for which the submitted answers in quiz submissions should be loaded
     */
    default void loadQuizSubmissionsSubmittedAnswers(Collection<StudentParticipation> participations) {
        List<QuizSubmission> quizSubmissions = participations.stream().filter(participation -> participation.getExercise() instanceof QuizExercise)
                .filter(participation -> participation.getSubmissions() != null).flatMap(participation -> participation.getSubmissions().stream())
                .filter(QuizSubmission.class::isInstance).map(QuizSubmission.class::cast).filter(quizSubmission -> quizSubmission.getId() != null).toList();
        if (quizSubmissions.isEmpty()) {
            return;
        }
        // submitted answers can only be lazy loaded in many cases, so we load them explicitly here. One query for all
        // quiz submissions of the student exam rather than one per submission: every extra query costs its own
        // transaction round trip, and an exam with several quizzes used to pay that per submission.
        Map<Long, Set<SubmittedAnswer>> answersBySubmissionId = findBySubmissionIdIn(quizSubmissions.stream().map(QuizSubmission::getId).collect(Collectors.toSet())).stream()
                .collect(Collectors.groupingBy(submittedAnswer -> submittedAnswer.getSubmission().getId(), Collectors.toSet()));
        for (QuizSubmission quizSubmission : quizSubmissions) {
            quizSubmission.setSubmittedAnswers(answersBySubmissionId.getOrDefault(quizSubmission.getId(), Set.of()));
        }
    }
}
