package de.tum.in.www1.artemis.service.dto;

import jakarta.annotation.Nonnull;

import de.tum.in.www1.artemis.domain.GradingInstruction;

public record GradingInstructionDTO(long id, double credits, String gradingScale, String instructionDescription, String feedback, int usageCount) {

    /**
     * Convert GradingInstruction to GradingInstructionDTO
     *
     * @param gradingInstruction GradingInstruction to convert
     * @return GradingInstructionDTO
     */
    public static GradingInstructionDTO of(@Nonnull GradingInstruction gradingInstruction) {
        return new GradingInstructionDTO(gradingInstruction.getId(), gradingInstruction.getCredits(), gradingInstruction.getGradingScale(),
                gradingInstruction.getInstructionDescription(), gradingInstruction.getFeedback(), gradingInstruction.getUsageCount());
    }
}
