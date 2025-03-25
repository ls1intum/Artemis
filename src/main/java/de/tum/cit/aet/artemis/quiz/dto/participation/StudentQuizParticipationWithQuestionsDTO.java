package de.tum.cit.aet.artemis.quiz.dto.participation;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.result.ResultBeforeEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentQuizParticipationWithQuestionsDTO(@JsonUnwrapped StudentQuizParticipationBaseDTO studentQuizParticipationBaseDTO, QuizExerciseWithQuestionsDTO exercise,
        Set<ResultBeforeEvaluationDTO> results) implements StudentQuizParticipationDTO {

    /**
     * Creates a StudentQuizParticipationWithQuestionsDTO object from a StudentParticipation object.
     *
     * @param studentParticipation the StudentParticipation object
     * @return the created StudentQuizParticipationWithQuestionsDTO object
     */
    public static StudentQuizParticipationWithQuestionsDTO of(final StudentParticipation studentParticipation) {
        Exercise participationExercise = studentParticipation.getExercise();
        if (!(participationExercise instanceof QuizExercise quizExercise)) {
            // TODO: Figure out error handling here
            return null;
        }
        // ToDo: Results is deprecated, will be removed after QuizView is removed
        return new StudentQuizParticipationWithQuestionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), QuizExerciseWithQuestionsDTO.of(quizExercise),
                studentParticipation.getResults().stream().map(ResultBeforeEvaluationDTO::of).collect(Collectors.toSet()));
    }

}
