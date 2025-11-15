package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingInstructionDTO(long id, double credits, String gradingScale, String instructionDescription, String feedback, int usageCount) {

    /**
     * Convert GradingInstruction to GradingInstructionDTO
     *
     * @param gradingInstruction GradingInstruction to convert
     * @return a GradingInstructionDTO based on the GradingInstruction
     */
    public static GradingInstructionDTO of(@NotNull GradingInstruction gradingInstruction) {
        return new GradingInstructionDTO(gradingInstruction.getId(), gradingInstruction.getCredits(), gradingInstruction.getGradingScale(),
                gradingInstruction.getInstructionDescription(), gradingInstruction.getFeedback(), gradingInstruction.getUsageCount());
    }

    public static GradingInstruction toEntity(@NotNull GradingInstructionDTO dto) {
        GradingInstruction gradingInstruction = new GradingInstruction();
        gradingInstruction.setId(dto.id());
        gradingInstruction.setCredits(dto.credits());
        gradingInstruction.setGradingScale(dto.gradingScale());
        gradingInstruction.setInstructionDescription(dto.instructionDescription());
        gradingInstruction.setFeedback(dto.feedback());
        gradingInstruction.setUsageCount(dto.usageCount());
        return gradingInstruction;
    }
}
