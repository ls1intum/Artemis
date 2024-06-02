package de.tum.in.www1.artemis.service.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.GradingInstruction;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingInstructionDTO(long id, double credits, String gradingScale, String instructionDescription, String feedback, int usageCount) {

    /**
     * Convert GradingInstruction to GradingInstructionDTO
     *
     * @param gradingInstruction GradingInstruction to convert
     * @return GradingInstructionDTO
     */
    public static GradingInstructionDTO of(@NotNull GradingInstruction gradingInstruction) {
        return new GradingInstructionDTO(gradingInstruction.getId(), gradingInstruction.getCredits(), gradingInstruction.getGradingScale(),
                gradingInstruction.getInstructionDescription(), gradingInstruction.getFeedback(), gradingInstruction.getUsageCount());
    }
}
