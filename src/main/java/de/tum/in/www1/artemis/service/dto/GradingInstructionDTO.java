package de.tum.in.www1.artemis.service.dto;

import de.tum.in.www1.artemis.domain.GradingInstruction;

public record GradingInstructionDTO(long id, double credits, String gradingScale, String instructionDescription, String feedback, int usageCount) {

    public static GradingInstructionDTO of(GradingInstruction gradingInstruction) {
        return new GradingInstructionDTO(gradingInstruction.getId(), gradingInstruction.getCredits(), gradingInstruction.getGradingScale(),
                gradingInstruction.getInstructionDescription(), gradingInstruction.getFeedback(), gradingInstruction.getUsageCount());
    }
}
