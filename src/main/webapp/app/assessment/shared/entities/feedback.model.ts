import { BaseEntity } from 'app/foundation/model/base-entity';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { convertToHtmlLinebreaks, escapeString } from 'app/foundation/util/text.utils';
import { ProgrammingExerciseTestCase, Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { GradingInstructionDTO } from 'app/exercise/shared/exercise-update-shared-dto.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';

export enum FeedbackHighlightColor {
    RED = 'rgba(219, 53, 69, 0.6)',
    // Apollon paints this behind an element's own white body, so a low alpha washed out to nothing on the canvas.
    CYAN = 'rgba(23, 162, 184, 0.6)',
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
    ADAPTED = 'ADAPTED', // Suggestion is accepted and then modified by the assessor
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
export interface FeedbackCorrectionError {
    // Corresponds to `Feedback.reference`. Reference to the assessed element.
    reference: string;

    // The correction type of the corresponding feedback.
    type: FeedbackCorrectionErrorType;
}

export type FeedbackCorrectionStatus = FeedbackCorrectionErrorType | 'CORRECT';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
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

    public static isFeedbackSuggestion(that: Feedback | string | undefined): boolean {
        const text = typeof that === 'object' ? that.text : that;
        if (!text) {
            return false;
        }
        return text.startsWith(FEEDBACK_SUGGESTION_IDENTIFIER);
    }

    public static isNonGradedFeedbackSuggestion(that: Feedback): boolean {
        if (!that.text) {
            return false;
        }
        return that.text.startsWith(NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER);
    }

    /**
     * Determine the type of the feedback suggestion. See FeedbackSuggestionType for more details on the meanings.
     * @param that feedback (or its bare `text`) to determine the type of
     */
    public static getFeedbackSuggestionType(that: Feedback | string | undefined): FeedbackSuggestionType {
        if (!Feedback.isFeedbackSuggestion(that)) {
            return FeedbackSuggestionType.NO_SUGGESTION;
        }
        // guaranteed to be defined here because isFeedbackSuggestion returned true, which requires non-empty text
        const text = typeof that === 'object' ? that.text! : that!;
        if (text.startsWith(FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER)) {
            return FeedbackSuggestionType.ADAPTED;
        }
        if (text.startsWith(FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER)) {
            return FeedbackSuggestionType.ACCEPTED;
        }
        return FeedbackSuggestionType.SUGGESTED;
    }

    /**
     * Strips the internal `FeedbackSuggestion:(suggested|accepted|adapted):` marker off a feedback's `text`, if
     * present. That marker exists only to tag the suggestion state in the database `text` column without a schema
     * change; it must never reach a tutor or a student as literal text.
     */
    public static stripSuggestionPrefix(text: string): string {
        for (const prefix of [FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER]) {
            if (text.startsWith(prefix)) {
                return text.slice(prefix.length);
            }
        }
        return text;
    }

    /** Translation keys of the points-based default titles a feedback gets when its title is left empty. */
    public static readonly DEFAULT_TITLE_KEYS = ['artemisApp.feedback.type.positive', 'artemisApp.feedback.type.needsRevision', 'artemisApp.feedback.type.feedback'];

    /** The translation key of the default title for the given points: "Positive", "Needs Revision" or "Feedback". */
    public static getDefaultTitleKey(credits: number | undefined): string {
        const points = credits ?? 0;
        return this.DEFAULT_TITLE_KEYS[points > 0 ? 0 : points < 0 ? 1 : 2];
    }

    /**
     * Rewrites an accepted feedback suggestion's `text` prefix to adapted, leaving everything else unchanged. A
     * suggestion transitions to adapted the moment it is edited in any way; every other state (already adapted,
     * not a suggestion, or the unreachable bare "suggested") is returned as-is. This is a one-way, sticky
     * transition - it never reverts even if the edit is undone later.
     */
    public static markAdaptedIfAcceptedSuggestion(text: string): string {
        if (!text.startsWith(FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER)) {
            return text;
        }
        return `${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}${text.slice(FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER.length)}`;
    }

    public static hasDetailText(that: Feedback): boolean {
        return that.detailText != undefined && that.detailText.length > 0;
    }

    public static hasContent(that: Feedback): boolean {
        // if the feedback is associated with the grading instruction, the detail text is optional
        return Feedback.hasDetailText(that) || !!that.gradingInstruction?.feedback;
    }

    /**
     * Whether `text` holds the feedback's title rather than its comment. It is a title once the feedback has a body of
     * its own, a description or the feedback of its grading instruction; feedback written before titles existed kept
     * its whole comment in `text`, which is then shown as the body under a default title.
     */
    public static isTextTitle(that: Feedback): boolean {
        return Feedback.hasContent(that);
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
        return Feedback.getReferenceLineRange(feedback)?.start;
    }

    /**
     * Get the referenced line range for referenced programming feedbacks, or undefined.
     * Typical reference format for programming feedback: `file:src/com/example/package/MyClass.java_line:13-15`.
     * Example output in this case: `{ start: 13, end: 15 }`
     * Lines are 0-based editor lines, so `_line:0` refers to the first line of the file.
     */
    public static getReferenceLineRange(feedback: Feedback): { start: number; end: number } | undefined {
        if (!feedback.reference?.startsWith(this.PROGRAMMING_REFERENCE_PREFIX)) {
            // Find "file:" prefix
            // No programming feedback
            return undefined;
        }
        const indexOfLine = feedback.reference.lastIndexOf(this.PROGRAMMING_REFERENCE_LINE_SEPERATOR); // Split before "_line:"
        const filePath = feedback.reference.substring(this.PROGRAMMING_REFERENCE_PREFIX.length, indexOfLine);
        if (indexOfLine <= this.PROGRAMMING_REFERENCE_PREFIX.length || !filePath.trim()) {
            return undefined;
        }
        const lineRange = feedback.reference.substring(indexOfLine + this.PROGRAMMING_REFERENCE_LINE_SEPERATOR.length).match(/^(\d+)(?:-(\d+))?$/);
        const start = Number(lineRange?.[1]);
        const end = Number(lineRange?.[2] ?? lineRange?.[1]);
        if (!lineRange || end < start) {
            return undefined;
        }
        return { start, end };
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

    public static haveCreditsAndComments(that: Feedback[]): boolean {
        return that.filter(Feedback.hasCreditsAndComment).length > 0 && that.filter(Feedback.hasCreditsAndComment).length === that.length;
    }

    public static forModeling(credits: number, text?: string, referenceId?: string, referenceType?: string, dropInfo?: DropInfo): Feedback {
        const that = new Feedback();
        that.referenceId = referenceId;
        that.referenceType = referenceType;
        that.credits = credits;
        that.text = text;
        // Apollon stores the GradingInstruction flat on dropInfo (not nested under dropInfo.instruction)
        // Support both: dropInfo.instruction.id (expected shape) and dropInfo.id (actual Apollon shape)
        const flatDropInfo = dropInfo as (GradingInstruction & { instruction?: GradingInstruction }) | undefined;
        const instruction = flatDropInfo?.instruction ?? (flatDropInfo?.id ? flatDropInfo : undefined);
        if (instruction?.id) {
            that.gradingInstruction = instruction;
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
        return hydrate(new Feedback(), response);
    }
}

/**
 * Whether the description of a feedback already contains the feedback text of its grading instruction, as it does once a
 * tutor drops a criterion on their own feedback. The criterion's text then must not be shown a second time.
 *
 * @param feedback the feedback to check
 * @returns true if the description contains the criterion's feedback text
 */
const isGradingInstructionTextInDetail = (feedback: Feedback): boolean => {
    const instructionText = feedback.gradingInstruction?.feedback;
    return !!instructionText && !!feedback.detailText?.includes(instructionText);
};

/**
 * The body a student reads for a feedback that may be linked to a grading instruction:
 * - An AI suggestion shows only its own description. Athena tends to restate the criterion's feedback text in it, so
 *   showing both would repeat the same sentence; the criterion's text is only the fallback for an empty description.
 * - A tutor's own feedback gets the criterion's text copied into its description when the criterion is dropped on it,
 *   so the description alone is shown. Feedback assessed before that, whose description does not contain the criterion's
 *   text, still shows both.
 *
 * @param feedback the feedback to read
 * @returns the body of the feedback, or undefined if it has none
 */
export const getFeedbackBodyText = (feedback: Feedback): string | undefined => {
    const instructionText = feedback.gradingInstruction?.feedback;
    if (!instructionText || !feedback.detailText) {
        return feedback.detailText || instructionText;
    }
    const isAIFeedback = Feedback.isFeedbackSuggestion(feedback) || Feedback.isNonGradedFeedbackSuggestion(feedback);
    if (isAIFeedback || isGradingInstructionTextInDetail(feedback)) {
        return feedback.detailText;
    }
    return instructionText + '\n' + feedback.detailText;
};

/**
 * Helper method to build the feedback text for the review. When the feedback has a link with grading instruction
 * its body is chosen by {@link getFeedbackBodyText}. Otherwise, it returns the detailed text and/or text properties
 * of the feedback depending on the submission element.
 *
 * An AI feedback suggestion's `text` is never included: it always holds just the suggestion's short title (tagged
 * with the internal `FeedbackSuggestion:...` marker), which is redundant with the suggestion's own `detailText`.
 * For text/programming/file-upload exercises that title is still shown separately (the editable unified feedback
 * editor's own title field, or the "name · title" heading in the read-only feedback item), so dropping it here
 * only avoids showing it twice. For modeling exercises the title ends up shown nowhere at all, since Apollon has
 * no separate title UI — that is safe because Apollon always writes an assessor's real edit into `detailText` and
 * leaves `.text` as the untouched original suggestion title (see `ModelingAssessmentComponent`), so no
 * assessor-authored content is ever hiding behind the excluded `text`.
 *
 * @param feedback that contains feedback text and grading instruction
 * @param addFeedbackText if the (non-suggestion) text of the feedback should be part of the resulting text.
 *                        Defaults to true. The detailText of the feedback is always added if present.
 * @returns formatted string representing the feedback text ready to display
 */
export const buildFeedbackTextForReview = (feedback: Feedback, addFeedbackText = true): string => {
    const includeText = addFeedbackText && !!feedback.text && !Feedback.isFeedbackSuggestion(feedback);
    // The text of a feedback linked to a criterion is its title, not its body (see Feedback.isTextTitle)
    let feedbackText = getFeedbackBodyText(feedback) ?? '';
    if (!feedbackText && includeText) {
        feedbackText = feedback.text!;
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
    const encounteredInstructions = new Map<number, number>(); // instructionId -> number of encounters
    for (const feedback of feedbacks) {
        if (feedback.gradingInstruction) {
            const instructionId = feedback.gradingInstruction.id!;
            const maxCount = feedback.gradingInstruction.usageCount ?? 0; // 0 means unlimited
            const encounters = encounteredInstructions.get(instructionId) ?? 0;

            encounteredInstructions.set(instructionId, encounters + 1);

            if (maxCount > 0 && encounters >= maxCount) {
                feedback.isSubsequent = true;
            }
        }
    }
};

/**
 * DTO representing feedback returned by the server.
 */
export interface FeedbackDTO {
    id?: number;
    text?: string;
    detailText?: string;
    hasLongFeedbackText?: boolean;
    reference?: string;
    credits?: number;
    positive?: boolean;
    type?: FeedbackType;
    visibility?: Visibility;
    testCaseName?: string;
    gradingInstruction?: GradingInstructionDTO;
}

export function convertFeedbackFromServer(dto: FeedbackDTO): Feedback {
    const feedback = new Feedback();

    feedback.id = dto.id;
    feedback.text = dto.text;
    feedback.detailText = dto.detailText;
    feedback.hasLongFeedbackText = dto.hasLongFeedbackText;
    feedback.reference = dto.reference;
    feedback.credits = dto.credits;
    feedback.positive = dto.positive;
    feedback.type = dto.type;
    feedback.testCase = dto.testCaseName
        ? {
              testName: dto.testCaseName,
              visibility: dto.visibility,
          }
        : undefined;
    if (dto.reference) {
        const split = dto.reference.split(':');
        if (split.length === 2) {
            feedback.referenceType = split[0];
            feedback.referenceId = split[1];
        }
    }
    if (dto.gradingInstruction) {
        const gradingInstruction: Partial<GradingInstruction> = {
            id: dto.gradingInstruction.id,
            feedback: dto.gradingInstruction.feedback,
            credits: dto.gradingInstruction.credits,
            usageCount: dto.gradingInstruction.usageCount,
            instructionDescription: dto.gradingInstruction.instructionDescription,
            gradingScale: dto.gradingInstruction.gradingScale,
        };
        feedback.gradingInstruction = gradingInstruction as GradingInstruction;
    }
    return feedback;
}

export function convertFeedbacksFromServer(dtos?: FeedbackDTO[]): Feedback[] {
    return dtos?.map((dto) => convertFeedbackFromServer(dto)) ?? [];
}
