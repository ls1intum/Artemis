package de.tum.cit.aet.artemis.service.dto.athena;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a Feedback on a ProgrammingExercise, for transferring data to Athena and receiving suggestions from Athena
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingFeedbackDTO(long id, long exerciseId, long submissionId, String title, String description, double credits, Long structuredGradingInstructionId,
        String filePath, Integer lineStart, Integer lineEnd) implements FeedbackBaseDTO {

    /**
     * Creates a ProgrammingFeedbackDTO from a Feedback object
     *
     * @param exerciseId   the id of the exercise the feedback is given for
     * @param submissionId the id of the submission the feedback is given for
     * @param feedback     the feedback object
     * @return the ProgrammingFeedbackDTO
     */
    public static ProgrammingFeedbackDTO of(long exerciseId, long submissionId, @NotNull de.tum.cit.aet.artemis.domain.Feedback feedback) {
        // Referenced feedback has a reference looking like this: "file:src/main/java/SomeFile.java_line:42"
        String filePath = null;
        Integer lineStart = null;
        final String referenceStart = "file:";
        if (feedback.hasReference() && feedback.getReference().startsWith(referenceStart)) {
            String[] referenceParts = feedback.getReference().split("_line:");
            filePath = referenceParts[0].substring(referenceStart.length());
            lineStart = Integer.parseInt(referenceParts[1]);
        }
        Long gradingInstructionId = null;
        if (feedback.getGradingInstruction() != null) {
            gradingInstructionId = feedback.getGradingInstruction().getId();
        }
        // There is only one line and Athena supports multiple lines, so we just take the line for both start and end
        return new ProgrammingFeedbackDTO(feedback.getId(), exerciseId, submissionId, feedback.getText(), feedback.getDetailText(), feedback.getCredits(), gradingInstructionId,
                filePath, lineStart, lineStart);
    }
}
