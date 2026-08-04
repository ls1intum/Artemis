package de.tum.cit.aet.artemis.iris.dto;

import java.io.Serializable;
import java.util.Optional;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;

/**
 * DTO for an Iris assessment, including the assessed student and exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentDTO(Long id, IrisVerdict verdict, IrisVerdictReview verdictReview, @Valid StudentIrisAssessmentDTO student, @Valid IrisAssessmentExerciseDTO exercise)
        implements Serializable {

    /**
     * Creates a DTO for the given Iris assessment.
     *
     * @param assessment the assessment to convert
     * @return the DTO or null if the assessment is null
     */
    @Nullable
    public static IrisAssessmentDTO of(@Nullable IrisAssessment assessment) {
        return Optional.ofNullable(assessment).map(value -> new IrisAssessmentDTO(value.getId(), value.getVerdict(), value.getVerdictReview(),
                StudentIrisAssessmentDTO.of(value.getStudent()), IrisAssessmentExerciseDTO.of(value.getExercise()))).orElse(null);
    }

    /**
     * DTO for the exercise an Iris assessment belongs to.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record IrisAssessmentExerciseDTO(Long id, String title, String type) implements Serializable {

        /**
         * Creates a DTO for the given exercise.
         *
         * @param exercise the exercise to convert
         * @return the DTO or null if the exercise is null
         */
        @Nullable
        public static IrisAssessmentExerciseDTO of(@Nullable Exercise exercise) {
            return Optional.ofNullable(exercise).map(value -> new IrisAssessmentExerciseDTO(value.getId(), value.getTitle(), value.getType())).orElse(null);
        }
    }
}
