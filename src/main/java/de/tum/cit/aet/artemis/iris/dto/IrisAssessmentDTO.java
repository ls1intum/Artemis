package de.tum.cit.aet.artemis.iris.dto;

import java.io.Serializable;
import java.util.Optional;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdictReview;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentDTO(Long id, IrisVerdict verdict, IrisVerdictReview verdictReview, @Valid StudentIrisAssessmentDTO student) implements Serializable {

    @Nullable
    public static IrisAssessmentDTO of(@Nullable IrisAssessment assessment) {
        return Optional.ofNullable(assessment)
                .map(value -> new IrisAssessmentDTO(value.getId(), value.getVerdict(), value.getVerdictReview(), StudentIrisAssessmentDTO.of(value.getStudent()))).orElse(null);
    }
}
