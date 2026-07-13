package de.tum.cit.aet.artemis.quiz.dto.participation;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionAfterEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentQuizParticipationWithSolutionsDTO(@JsonUnwrapped StudentQuizParticipationBaseDTO studentQuizParticipationBaseDTO, QuizExerciseWithSolutionDTO exercise,
        Set<QuizSubmissionAfterEvaluationDTO> submissions) implements StudentQuizParticipationDTO {

    /**
     * Creates a StudentQuizParticipationWithSolutionsDTO object from a StudentParticipation object.
     *
     * @param studentParticipation the StudentParticipation object
     * @return the created StudentQuizParticipationWithSolutionsDTO object
     */
    public static StudentQuizParticipationWithSolutionsDTO of(final StudentParticipation studentParticipation) {
        return of(studentParticipation, false);
    }

    /**
     * Creates the participation embedded in a practice-result response without redundant course and nested result data.
     *
     * @param studentParticipation the practice participation
     * @return the practice response projection
     */
    public static StudentQuizParticipationWithSolutionsDTO forPractice(final StudentParticipation studentParticipation) {
        return of(studentParticipation, true);
    }

    private static StudentQuizParticipationWithSolutionsDTO of(final StudentParticipation studentParticipation, boolean practiceResponse) {
        Exercise participationExercise = studentParticipation.getExercise();
        Set<Submission> submissions = studentParticipation.getSubmissions();
        if (!(participationExercise instanceof QuizExercise quizExercise)) {
            return null;
        }

        if (!Hibernate.isInitialized(submissions) || submissions == null) {
            submissions = Set.of();
        }
        submissions = submissions.stream().filter(QuizSubmission.class::isInstance).collect(Collectors.toSet());

        Set<QuizSubmissionAfterEvaluationDTO> submissionsAfterEvaluation = submissions.stream().map(QuizSubmission.class::cast)
                .map(submission -> practiceResponse ? QuizSubmissionAfterEvaluationDTO.forPractice(submission) : QuizSubmissionAfterEvaluationDTO.of(submission))
                .collect(Collectors.toSet());
        QuizExerciseWithSolutionDTO exerciseDTO = practiceResponse ? QuizExerciseWithSolutionDTO.forPractice(quizExercise) : QuizExerciseWithSolutionDTO.of(quizExercise);

        return new StudentQuizParticipationWithSolutionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), exerciseDTO, submissionsAfterEvaluation);
    }

}
