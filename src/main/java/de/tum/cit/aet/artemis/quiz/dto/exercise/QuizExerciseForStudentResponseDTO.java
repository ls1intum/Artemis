package de.tum.cit.aet.artemis.quiz.dto.exercise;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Quiz exercise response for a student. The quiz state selects the response shape and controls whether questions or solutions are included.
 */
@Schema(discriminatorProperty = "quizQuestionsType", discriminatorMapping = {
        @DiscriminatorMapping(value = "before-quiz-start", schema = QuizExerciseWithoutQuestionsForStudentDTO.class),
        @DiscriminatorMapping(value = "live-quiz", schema = QuizExerciseWithQuestionsForStudentDTO.class),
        @DiscriminatorMapping(value = "after-quiz-end", schema = QuizExerciseWithSolutionForStudentDTO.class) }, oneOf = { QuizExerciseWithoutQuestionsForStudentDTO.class,
                QuizExerciseWithQuestionsForStudentDTO.class, QuizExerciseWithSolutionForStudentDTO.class })
@JsonSubTypes({ @JsonSubTypes.Type(value = QuizExerciseWithoutQuestionsForStudentDTO.class, name = QuizExerciseForStudentResponseDTO.BEFORE_QUIZ_START),
        @JsonSubTypes.Type(value = QuizExerciseWithQuestionsForStudentDTO.class, name = QuizExerciseForStudentResponseDTO.LIVE_QUIZ),
        @JsonSubTypes.Type(value = QuizExerciseWithSolutionForStudentDTO.class, name = QuizExerciseForStudentResponseDTO.AFTER_QUIZ_END) })
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "quizQuestionsType", visible = true)
public sealed interface QuizExerciseForStudentResponseDTO
        permits QuizExerciseWithoutQuestionsForStudentDTO, QuizExerciseWithQuestionsForStudentDTO, QuizExerciseWithSolutionForStudentDTO {

    String BEFORE_QUIZ_START = "before-quiz-start";

    String LIVE_QUIZ = "live-quiz";

    String AFTER_QUIZ_END = "after-quiz-end";

    static QuizExerciseForStudentResponseDTO beforeQuizStart(QuizExerciseWithoutQuestionsDTO quizExercise) {
        return new QuizExerciseWithoutQuestionsForStudentDTO(quizExercise, BEFORE_QUIZ_START);
    }

    static QuizExerciseForStudentResponseDTO liveQuiz(QuizExerciseWithQuestionsDTO quizExercise) {
        return new QuizExerciseWithQuestionsForStudentDTO(quizExercise, LIVE_QUIZ);
    }

    static QuizExerciseForStudentResponseDTO afterQuizEnd(QuizExerciseWithSolutionDTO quizExercise) {
        return new QuizExerciseWithSolutionForStudentDTO(quizExercise, AFTER_QUIZ_END);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizExerciseWithoutQuestionsForStudentDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = QuizExerciseForStudentResponseDTO.BEFORE_QUIZ_START, defaultValue = QuizExerciseForStudentResponseDTO.BEFORE_QUIZ_START) String quizQuestionsType)
        implements QuizExerciseForStudentResponseDTO {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizExerciseWithQuestionsForStudentDTO(@JsonUnwrapped QuizExerciseWithQuestionsDTO quizExercise,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = QuizExerciseForStudentResponseDTO.LIVE_QUIZ, defaultValue = QuizExerciseForStudentResponseDTO.LIVE_QUIZ) String quizQuestionsType)
        implements QuizExerciseForStudentResponseDTO {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizExerciseWithSolutionForStudentDTO(@JsonUnwrapped QuizExerciseWithSolutionDTO quizExercise,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = QuizExerciseForStudentResponseDTO.AFTER_QUIZ_END, defaultValue = QuizExerciseForStudentResponseDTO.AFTER_QUIZ_END) String quizQuestionsType)
        implements QuizExerciseForStudentResponseDTO {
}
