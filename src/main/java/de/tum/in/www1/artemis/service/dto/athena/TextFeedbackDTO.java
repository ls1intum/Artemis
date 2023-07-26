package de.tum.in.www1.artemis.service.dto.athena;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextBlockRef;
import de.tum.in.www1.artemis.domain.TextSubmission;

/**
 * A DTO representing a Feedback, for transferring data to Athena
 */
public record TextFeedbackDTO(long id, long exerciseId, long submissionId, String text, String detailText, double credits, Long gradingInstructionId, Integer indexStart,
        Integer indexEnd) {

    /**
     * Creates a TextFeedbackDTO from a Feedback object
     *
     * @param exerciseId    the id of the exercise the feedback is given for
     * @param submissionId  the id of the submission the feedback is given for
     * @param feedback      the feedback object
     * @param feedbackBlock the TextBlock that the feedback is on (must be passed because this record cannot fetch it for itself)
     * @return the TextFeedbackDTO
     */
    public static TextFeedbackDTO of(long exerciseId, long submissionId, @NotNull Feedback feedback, TextBlock feedbackBlock) {
        Integer startIndex = feedbackBlock == null ? null : feedbackBlock.getStartIndex();
        Integer endIndex = feedbackBlock == null ? null : feedbackBlock.getEndIndex();
        return new TextFeedbackDTO(feedback.getId(), exerciseId, submissionId, feedback.getText(), feedback.getDetailText(), feedback.getCredits(),
                feedback.getGradingInstruction().getId(), startIndex, endIndex);
    }

    /**
     * Creates a TextBlockRef (feedback + text block combined) from this DTO and a TextSubmission
     */
    public TextBlockRef toTextBlockRef(TextSubmission onSubmission) {
        Feedback feedback = new Feedback();
        feedback.setId(id());
        feedback.setText(text());
        feedback.setDetailText(detailText());
        feedback.setCredits(credits());

        TextBlock textBlock = new TextBlock();
        textBlock.setStartIndex(indexStart());
        textBlock.setEndIndex(indexEnd());
        textBlock.setText(onSubmission.getText().substring(indexStart(), indexEnd()));

        return new TextBlockRef(textBlock, feedback);
    }
}
