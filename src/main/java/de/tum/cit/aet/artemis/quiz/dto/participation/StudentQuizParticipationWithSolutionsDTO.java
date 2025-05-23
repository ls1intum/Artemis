package de.tum.cit.aet.artemis.quiz.dto.participation;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.result.ResultAfterEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentQuizParticipationWithSolutionsDTO(@JsonUnwrapped StudentQuizParticipationBaseDTO studentQuizParticipationBaseDTO, QuizExerciseWithSolutionDTO exercise,
        Set<ResultAfterEvaluationDTO> results) implements StudentQuizParticipationDTO {

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
        // ToDo: Results is deprecated, will be removed after QuizView is removed
        return new StudentQuizParticipationWithSolutionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), QuizExerciseWithSolutionDTO.of(quizExercise),
                studentParticipation.getResults().stream().map(ResultAfterEvaluationDTO::of).collect(Collectors.toSet()));
    }

}
