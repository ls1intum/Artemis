package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingInstructionDTO(@NotNull Long id, double credits, String gradingScale, String instructionDescription, String feedback, int usageCount) {

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

    /**
     * Converts this DTO into a {@link GradingInstruction} entity.
     *
     * @param gradingInstructionDTO the DTO to convert
     * @return a new {@link GradingInstruction} with values copied from the DTO
     */
    public static GradingInstruction toEntity(@NotNull GradingInstructionDTO gradingInstructionDTO) {
        GradingInstruction gradingInstruction = new GradingInstruction();
        gradingInstruction.setId(gradingInstructionDTO.id());
        gradingInstruction.setCredits(gradingInstructionDTO.credits());
        gradingInstruction.setGradingScale(gradingInstructionDTO.gradingScale());
        gradingInstruction.setInstructionDescription(gradingInstructionDTO.instructionDescription());
        gradingInstruction.setFeedback(gradingInstructionDTO.feedback());
        gradingInstruction.setUsageCount(gradingInstructionDTO.usageCount());
        return gradingInstruction;
    }
}
