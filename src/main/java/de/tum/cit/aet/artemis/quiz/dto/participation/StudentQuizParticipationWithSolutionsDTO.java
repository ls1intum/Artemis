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
        Exercise participationExercise = studentParticipation.getExercise();
        Set<Submission> submissions = studentParticipation.getSubmissions();
        if (!(participationExercise instanceof QuizExercise quizExercise)) {
            // Return null if the exercise is not a QuizExercise
            return null;
        }

        if (!Hibernate.isInitialized(submissions) || submissions == null) {
            submissions = Set.of();
        }
        submissions = submissions.stream().filter(submission -> submission instanceof QuizSubmission).collect(Collectors.toSet());

        Set<QuizSubmissionAfterEvaluationDTO> submissionsAfterEvaluation = submissions.stream().map(submission -> (QuizSubmission) submission)
                .map(QuizSubmissionAfterEvaluationDTO::of).collect(Collectors.toSet());

        return new StudentQuizParticipationWithSolutionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), QuizExerciseWithSolutionDTO.of(quizExercise),
                submissionsAfterEvaluation);
    }

}
