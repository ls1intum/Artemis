import { BaseEntity } from 'app/shared/model/base-entity';
import { Result } from 'app/entities/result.model';
import { TextBlock } from 'app/entities/text-block.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { FeedbackConflict } from 'app/entities/feedback-conflict';
import { convertToHtmlLinebreaks } from 'app/utils/text.utils';

export enum FeedbackHighlightColor {
    RED = 'rgba(219, 53, 69, 0.6)',
    CYAN = 'rgba(23, 162, 184, 0.3)',
    BLUE = 'rgba(0, 123, 255, 0.6)',
    YELLOW = 'rgba(255, 193, 7, 0.6)',
    GREEN = 'rgba(40, 167, 69, 0.6)',
}

export enum FeedbackType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
    MANUAL_UNREFERENCED = 'MANUAL_UNREFERENCED',
    AUTOMATIC_ADAPTED = 'AUTOMATIC_ADAPTED',
}

export const STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER = 'SCAFeedbackIdentifier:';
export const SUBMISSION_POLICY_FEEDBACK_IDENTIFIER = 'SubPolFeedbackIdentifier:';

export interface DropInfo {
    instruction: GradingInstruction;
}

/**
 * Possible tutor feedback states upon validation from the server.
 */
export enum FeedbackCorrectionErrorType {
    INCORRECT_SCORE = 'INCORRECT_SCORE',
    UNNECESSARY_FEEDBACK = 'UNNECESSARY_FEEDBACK',
    MISSING_GRADING_INSTRUCTION = 'MISSING_GRADING_INSTRUCTION',
    INCORRECT_GRADING_INSTRUCTION = 'INCORRECT_GRADING_INSTRUCTION',
    EMPTY_NEGATIVE_FEEDBACK = 'EMPTY_NEGATIVE_FEEDBACK',
}

/**
 * Wraps the information returned by the server upon validating tutor feedbacks.
 */
export class FeedbackCorrectionError {
    // Corresponds to `Feedback.reference`. Reference to the assessed element.
    public reference: string;

    // The correction type of the corresponding feedback.
    public type: FeedbackCorrectionErrorType;
}

export type FeedbackCorrectionStatus = FeedbackCorrectionErrorType | 'CORRECT';

export class Feedback implements BaseEntity {
    public id?: number;
    public gradingInstruction?: GradingInstruction;
    public text?: string;
    public detailText?: string;
    public reference?: string;
    public credits?: number;
    public type?: FeedbackType;
    public result?: Result;
    public positive?: boolean;
    public conflictingTextAssessments?: FeedbackConflict[];
    public suggestedFeedbackReference?: string;
    public suggestedFeedbackOriginSubmissionReference?: number;
    public suggestedFeedbackParticipationReference?: number;

    // Specifies whether or not the tutor feedback is correct relative to the instructor feedback (during tutor training) or if there is a validation error.
    // Client only property.
    public correctionStatus?: FeedbackCorrectionStatus;

    // helper attributes for modeling exercise assessments stored in Feedback
    public referenceType?: string; // this string needs to follow UMLModelElementType in Apollon in typings.d.ts
    public referenceId?: string;

    public copiedFeedbackId?: number; // helper attribute, only calculated locally on the client

    public isSubsequent?: boolean; // helper attribute to find feedback which is not included in the total score on the client

    constructor() {
        this.credits = 0;
    }

    public static isTestCaseFeedback(feedback: Feedback): boolean {
        if (feedback.type !== FeedbackType.AUTOMATIC) {
            return false;
        }
        if (!feedback.text) {
            return true;
        }
        return !feedback.text.includes(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, 0) && !feedback.text.includes(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER, 0);
    }

    public static isStaticCodeAnalysisFeedback(that: Feedback): boolean {
        if (!that.text) {
            return false;
        }
        return that.type === FeedbackType.AUTOMATIC && that.text.includes(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, 0);
    }

    public static isSubmissionPolicyFeedback(that: Feedback): boolean {
        if (!that.text) {
            return false;
        }
        return that.type === FeedbackType.AUTOMATIC && that.text.includes(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER, 0);
    }

    public static hasDetailText(that: Feedback): boolean {
        return that.detailText != undefined && that.detailText.length > 0;
    }

    public static hasContent(that: Feedback): boolean {
        // if the feedback is associated with the grading instruction, the detail text is optional
        return Feedback.hasDetailText(that) || !!(that.gradingInstruction && that.gradingInstruction.feedback);
    }

    /**
     * Feedback is empty if it has 0 credits and the comment is empty.
     * @param that
     */
    public static isEmpty(that: Feedback): boolean {
        return !that.credits && !Feedback.hasContent(that);
    }

    /**
     * Feedback is present if it has non 0 credits, a comment, or both.
     * @param that
     */
    public static isPresent(that: Feedback): boolean {
        return !Feedback.isEmpty(that);
    }

    public static hasCreditsAndComment(that: Feedback): boolean {
        return that.credits != undefined && Feedback.hasContent(that);
    }

    public static haveCredits(that: Feedback[]): boolean {
        return that.filter(Feedback.hasCredits).length > 0 && that.filter(Feedback.hasCredits).length === that.length;
    }

    public static hasCredits(that: Feedback): boolean {
        return that.credits != undefined;
    }

    public static haveCreditsAndComments(that: Feedback[]): boolean {
        return that.filter(Feedback.hasCreditsAndComment).length > 0 && that.filter(Feedback.hasCreditsAndComment).length === that.length;
    }

    public static forModeling(credits: number, text?: string, referenceId?: string, referenceType?: string, dropInfo?: DropInfo): Feedback {
        const that = new Feedback();
        that.referenceId = referenceId;
        that.referenceType = referenceType;
        that.credits = credits;
        that.text = text;
        if (dropInfo && dropInfo.instruction?.id) {
            that.gradingInstruction = dropInfo.instruction;
        }
        if (referenceType && referenceId) {
            that.reference = referenceType + ':' + referenceId;
        }
        return that;
    }

    public static forText(textBlock: TextBlock, credits = 0, detailText?: string): Feedback {
        const that = new Feedback();
        that.reference = textBlock.id;
        that.credits = credits;
        that.detailText = detailText;

        // Delete unused properties
        that.referenceId = undefined;
        that.referenceType = undefined;
        that.text = undefined;
        that.positive = undefined;

        return that;
    }

    public static fromServerResponse(response: Feedback): Feedback {
        return Object.assign(new Feedback(), response);
    }

    public static updateFeedbackTypeOnChange(feedback: Feedback) {
        if (feedback.type === FeedbackType.AUTOMATIC) {
            feedback.type = FeedbackType.AUTOMATIC_ADAPTED;
        }
    }
}

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
 * @param assessment the list of feedback provided in the assessment
 */
export const checkSubsequentFeedbackInAssessment = (assessment: Feedback[]) => {
    const gradingInstructions = {}; // { instructionId: number of encounters }
    for (const feedback of assessment) {
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
