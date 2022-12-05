import { convertToHtmlLinebreaks } from 'app/utils/text.utils';
import { Feedback } from 'app/entities/feedback.model';

/**
 * Helper method to build the feedback text for the review. When the feedback has a link with grading instruction
 * it merges the feedback of the grading instruction with the feedback text provided by the assessor. Otherwise,
 * it returns the detailed text and/or text properties of the feedback depending on the submission element.
 *
 * @param feedback that contains feedback text and grading instruction
 * @param addFeedbackText if the text of the feedback should be part of the resulting text. Defaults to true.
 *                        The detailText of the feedback is always added if present.
 * @returns formatted string representing the feedback text ready to display
 */
export const buildFeedbackTextForReview = (feedback: Feedback, addFeedbackText = true): string => {
    let feedbackText = '';
    if (feedback.gradingInstruction && feedback.gradingInstruction.feedback) {
        feedbackText = feedback.gradingInstruction.feedback;
        if (feedback.detailText) {
            feedbackText = feedbackText + '\n' + feedback.detailText;
        }
        if (addFeedbackText && feedback.text) {
            feedbackText = feedbackText + '\n' + feedback.text;
        }
    } else if (feedback.detailText) {
        feedbackText = feedback.detailText;
    } else if (addFeedbackText && feedback.text) {
        feedbackText = feedback.text;
    }

    return convertToHtmlLinebreaks(feedbackText);
};

/**
 * Helper method to find subsequent feedback for the review. When the feedback has a link with grading instruction,
 * it keeps the number of how many times the grading instructions are applied. If the usage limit is exceeded for the
 * grading instruction, it marks the feedback as subsequent.
 *
 * @param feedbacks the list of feedbacks
 */
export const checkSubsequentFeedbackInAssessment = (feedbacks: Feedback[]) => {
    const gradingInstructions = {}; // { instructionId: number of encounters }
    for (const feedback of feedbacks) {
        if (feedback.gradingInstruction && feedback.gradingInstruction.credits !== 0) {
            if (gradingInstructions[feedback.gradingInstruction!.id!]) {
                // this grading instruction is counted before
                const maxCount = feedback.gradingInstruction.usageCount;
                const encounters = gradingInstructions[feedback.gradingInstruction!.id!];
                if (maxCount && maxCount > 0) {
                    if (encounters >= maxCount) {
                        // usage limit is exceeded, mark the feedback as subsequent
                        feedback.isSubsequent = true;
                    }
                    gradingInstructions[feedback.gradingInstruction!.id!] = encounters + 1;
                }
            } else {
                // the grading instruction is encountered for the first time
                gradingInstructions[feedback.gradingInstruction!.id!] = 1;
            }
        }
    }
};
