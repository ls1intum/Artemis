import { BaseEntity } from 'app/shared/model/base-entity';
import { Result } from 'app/entities/result.model';
import { TextBlock } from 'app/entities/text/text-block.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { convertToHtmlLinebreaks, escapeString } from 'app/utils/text.utils';
import { ProgrammingExerciseTestCase } from 'app/entities/programming/programming-exercise-test-case.model';

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

export enum FeedbackSuggestionType {
    NO_SUGGESTION = 'NO_SUGGESTION', // No suggestion at all
    SUGGESTED = 'SUGGESTED', // Suggestion is made, but not accepted yet
    ACCEPTED = 'ACCEPTED', // Suggestion is accepted
    ADAPTED = 'ADAPTED', // Suggestion is accepted and then modified by the TA
}

// Prefixes for the feedback text to identify the feedback type more specifically without having to change the database schema:
export const STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER = 'SCAFeedbackIdentifier:';
export const SUBMISSION_POLICY_FEEDBACK_IDENTIFIER = 'SubPolFeedbackIdentifier:';
export const FEEDBACK_SUGGESTION_IDENTIFIER = 'FeedbackSuggestion:';
export const FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER = 'FeedbackSuggestion:accepted:';
export const FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER = 'FeedbackSuggestion:adapted:';
export const NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER = 'NonGradedFeedbackSuggestion:';

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
    public hasLongFeedbackText?: boolean;
    public reference?: string;
    public credits?: number;
    public type?: FeedbackType;
    public result?: Result;
    public positive?: boolean;
    public testCase?: ProgrammingExerciseTestCase;

    // Specifies whether the tutor feedback is correct relative to the instructor feedback (during tutor training) or if there is a validation error.
    // Client only property.
    public correctionStatus?: FeedbackCorrectionStatus;

    // helper attributes for modeling exercise assessments stored in Feedback
    public referenceType?: string; // this string needs to follow UMLModelElementType in Apollon in typings.d.ts
    public referenceId?: string;

    public copiedFeedbackId?: number; // helper attribute, only calculated locally on the client

    public isSubsequent?: boolean; // helper attribute to find feedback which is not included in the total score on the client

    private static readonly PROGRAMMING_REFERENCE_PREFIX = 'file:';
    private static readonly PROGRAMMING_REFERENCE_LINE_SEPERATOR = '_line:';

    constructor() {
        this.credits = 0;
    }

    public static isTestCaseFeedback(feedback: Feedback): boolean {
        if (feedback.type !== FeedbackType.AUTOMATIC) {
            return false;
        }
        return !!feedback.testCase;
    }

    public static isStaticCodeAnalysisFeedback(that: Feedback): boolean {
        if (!that.text) {
            return false;
        }
        return that.type === FeedbackType.AUTOMATIC && that.text.startsWith(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER);
    }

    public static isSubmissionPolicyFeedback(that: Feedback): boolean {
        if (!that.text) {
            return false;
        }
        return that.type === FeedbackType.AUTOMATIC && that.text.startsWith(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER);
    }

    public static isFeedbackSuggestion(that: Feedback): boolean {
        if (!that.text) {
            return false;
        }
        return that.text.startsWith(FEEDBACK_SUGGESTION_IDENTIFIER);
    }

    public static isNonGradedFeedbackSuggestion(that: Feedback): boolean {
        if (!that.text) {
            return false;
        }
        return that.text.startsWith(NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER);
    }

    /**
     * Determine the type of the feedback suggestion. See FeedbackSuggestionType for more details on the meanings.
     * @param that feedback to determine the type of
     */
    public static getFeedbackSuggestionType(that: Feedback): FeedbackSuggestionType {
        if (!Feedback.isFeedbackSuggestion(that)) {
            return FeedbackSuggestionType.NO_SUGGESTION;
        }
        // that.text is guaranteed to be defined here because the feedback is a suggestion, which must have a text
        if (that.text!.startsWith(FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER)) {
            return FeedbackSuggestionType.ACCEPTED;
        }
        if (that.text!.startsWith(FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER)) {
            return FeedbackSuggestionType.ADAPTED;
        }
        return FeedbackSuggestionType.SUGGESTED;
    }

    public static hasDetailText(that: Feedback): boolean {
        return that.detailText != undefined && that.detailText.length > 0;
    }

    public static hasContent(that: Feedback): boolean {
        // if the feedback is associated with the grading instruction, the detail text is optional
        return Feedback.hasDetailText(that) || !!that.gradingInstruction?.feedback;
    }

    /**
     * Checks for equality of two feedbacks. Only checking the ids is not enough because they are undefined for inline
     * feedbacks before they are saved.
     * @param f1 The feedback that is compared to f2
     * @param f2 The feedback that is compared to f1
     */
    public static areIdentical(f1: Feedback, f2: Feedback) {
        return f1.id === f2.id && f1.text === f2.text && f1.detailText === f2.detailText;
    }

    /**
     * Get the referenced file path for referenced programming feedbacks, or undefined.
     * Typical reference format for programming feedback: `file:src/com/example/package/MyClass.java_line:13`.
     * Example output in this case: `src/com/example/package/MyClass.java`
     */
    public static getReferenceFilePath(feedback: Feedback): string | undefined {
        if (!feedback.reference?.startsWith(this.PROGRAMMING_REFERENCE_PREFIX)) {
            // Find "file:" prefix
            // No programming feedback
            return undefined;
        }
        const indexOfLine = feedback.reference?.lastIndexOf(this.PROGRAMMING_REFERENCE_LINE_SEPERATOR);
        return feedback.reference.substring(this.PROGRAMMING_REFERENCE_PREFIX.length, indexOfLine); // Split after "_line:"
    }

    /**
     * Get the referenced line for referenced programming feedbacks, or undefined.
     * Typical reference format for programming feedback: `file:src/com/example/package/MyClass.java_line:13`.
     * Example output in this case: 13
     */
    public static getReferenceLine(feedback: Feedback): number | undefined {
        if (!feedback.reference?.startsWith(this.PROGRAMMING_REFERENCE_PREFIX)) {
            // Find "file:" prefix
            // No programming feedback
            return undefined;
        }
        const indexOfLine = feedback.reference.lastIndexOf(this.PROGRAMMING_REFERENCE_LINE_SEPERATOR); // Split before "_line:"
        const line = parseInt(feedback.reference.substring(indexOfLine + this.PROGRAMMING_REFERENCE_LINE_SEPERATOR.length));
        if (isNaN(line)) {
            return undefined;
        }
        return line;
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
        if (dropInfo?.instruction?.id) {
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
        if (Feedback.isFeedbackSuggestion(feedback)) {
            // Mark as adapted feedback suggestion
            feedback.text = (feedback.text ?? FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER).replace(FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER);
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
    if (feedback.gradingInstruction?.feedback) {
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

    // escape special characters like "<", ">", "&" to render them correctly
    feedbackText = escapeString(feedbackText);
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
    const gradingInstructions: { [key: number]: number } = {}; // { instructionId: number of encounters }
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
