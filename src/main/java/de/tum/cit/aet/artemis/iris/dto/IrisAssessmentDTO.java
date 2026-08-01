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

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentDTO(Long id, IrisVerdict verdict, IrisVerdictReview verdictReview, @Valid StudentIrisAssessmentDTO student, @Valid IrisAssessmentExerciseDTO exercise)
        implements Serializable {

    @Nullable
    public static IrisAssessmentDTO of(@Nullable IrisAssessment assessment) {
        return Optional.ofNullable(assessment).map(value -> new IrisAssessmentDTO(value.getId(), value.getVerdict(), value.getVerdictReview(),
                StudentIrisAssessmentDTO.of(value.getStudent()), IrisAssessmentExerciseDTO.of(value.getExercise()))).orElse(null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record IrisAssessmentExerciseDTO(Long id, String title, String type) implements Serializable {

        @Nullable
        public static IrisAssessmentExerciseDTO of(@Nullable Exercise exercise) {
            return Optional.ofNullable(exercise).map(value -> new IrisAssessmentExerciseDTO(value.getId(), value.getTitle(), value.getType())).orElse(null);
        }
    }
}
