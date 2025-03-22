package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultAfterEvaluationDTO(@JsonUnwrapped ResultBeforeEvaluationDTO resultBeforeEvaluationDTO, Boolean successful, Double score, AssessmentType assessmentType) {

    public static ResultAfterEvaluationDTO of(final Result result) {
        return new ResultAfterEvaluationDTO(ResultBeforeEvaluationDTO.of(result), result.isSuccessful(), result.getScore(), result.getAssessmentType());
    }

}
