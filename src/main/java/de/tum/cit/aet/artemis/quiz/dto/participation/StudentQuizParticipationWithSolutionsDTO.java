package de.tum.cit.aet.artemis.quiz.dto.participation;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
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
        if (!(participationExercise instanceof QuizExercise quizExercise)) {
            // TODO: Figure out error handling here
            return null;
        }
        if (!studentParticipation.getSubmissions().stream().allMatch(submission -> submission instanceof QuizSubmission)) {
            // TODO: Handle error if one or more submissions are not QuizSubmissions
            return null;
        }
        Set<QuizSubmissionAfterEvaluationDTO> submissions = studentParticipation.getSubmissions().stream().map(submission -> (QuizSubmission) submission)
                .map(QuizSubmissionAfterEvaluationDTO::of).collect(Collectors.toSet());
        // ToDo: Results is deprecated, will be removed after QuizView is removed
        return new StudentQuizParticipationWithSolutionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), QuizExerciseWithSolutionDTO.of(quizExercise), submissions);
    }

}
