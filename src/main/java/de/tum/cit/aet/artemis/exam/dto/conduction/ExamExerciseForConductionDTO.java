package de.tum.cit.aet.artemis.exam.dto.conduction;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Polymorphic projection of an {@link Exercise} in the conduction payload. The common fields live in
 * {@link ExamExerciseBaseForConductionDTO}; the per-type fields (only one of which is non-null) are unwrapped so the
 * wire stays flat and byte-compatible with the entity payload the (unchanged) client model deserializes.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamExerciseForConductionDTO(@JsonUnwrapped ExamExerciseBaseForConductionDTO base, @Nullable @JsonUnwrapped QuizExerciseForConductionDTO quizExercise,
        @Nullable @JsonUnwrapped ProgrammingExerciseForConductionDTO programmingExercise, @Nullable @JsonUnwrapped ModelingExerciseForConductionDTO modelingExercise,
        @Nullable @JsonUnwrapped FileUploadExerciseForConductionDTO fileUploadExercise) {

    /**
     * Converts an Exercise into an ExamExerciseForConductionDTO, dispatching on the concrete exercise type for the
     * per-type fields.
     *
     * @param exercise             the exercise to convert (never null; callers filter null elements before mapping)
     * @param includeQuizSolutions whether a quiz exercise's questions should carry their full solutions ({@code true}
     *                                 only once the student exam's results are published, decided by the summary caller)
     * @return the converted DTO
     */
    public static ExamExerciseForConductionDTO of(Exercise exercise, boolean includeQuizSolutions) {
        QuizExerciseForConductionDTO quizExercise = null;
        ProgrammingExerciseForConductionDTO programmingExercise = null;
        ModelingExerciseForConductionDTO modelingExercise = null;
        FileUploadExerciseForConductionDTO fileUploadExercise = null;
        switch (exercise) {
            case QuizExercise quiz -> quizExercise = QuizExerciseForConductionDTO.of(quiz, includeQuizSolutions);
            case ProgrammingExercise programming -> programmingExercise = ProgrammingExerciseForConductionDTO.of(programming);
            case ModelingExercise modeling -> modelingExercise = ModelingExerciseForConductionDTO.of(modeling);
            case FileUploadExercise fileUpload -> fileUploadExercise = FileUploadExerciseForConductionDTO.of(fileUpload);
            default -> {
                // text exercises carry no additional conduction fields beyond the common base
            }
        }
        return new ExamExerciseForConductionDTO(ExamExerciseBaseForConductionDTO.of(exercise), quizExercise, programmingExercise, modelingExercise, fileUploadExercise);
    }
}
