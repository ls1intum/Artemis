package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingInstructionDTO(@NotNull(message = "The ID does not exist") Long id, double credits, String gradingScale, String instructionDescription, String feedback,
        int usageCount) {

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
     * Creates a detached {@link GradingInstruction} from this DTO.
     * Intended for creation or non-managed use. For updating an existing
     * managed entity, prefer {@link #applyTo(GradingInstruction)}.
     */
    public GradingInstruction toEntity() {
        GradingInstruction gradingInstruction = new GradingInstruction();
        gradingInstruction.setId(id);
        gradingInstruction.setCredits(credits);
        gradingInstruction.setGradingScale(gradingScale);
        gradingInstruction.setInstructionDescription(instructionDescription);
        gradingInstruction.setFeedback(feedback);
        gradingInstruction.setUsageCount(usageCount);
        return gradingInstruction;
    }

    /**
     * Applies this DTO's data to an existing managed {@link GradingInstruction}.
     * Intended for update scenarios within a persistence context.
     */
    public void applyTo(@NotNull GradingInstruction gradingInstruction) {
        gradingInstruction.setCredits(this.credits);
        gradingInstruction.setGradingScale(this.gradingScale);
        gradingInstruction.setInstructionDescription(this.instructionDescription);
        gradingInstruction.setFeedback(this.feedback);
        gradingInstruction.setUsageCount(this.usageCount);
    }
}
