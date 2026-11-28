import { FeedbackDTO, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { FileUploadFeedback, FileUploadFeedbackTypeEnum, FileUploadFeedbackVisibilityEnum } from 'app/openapi/model/file-upload-feedback';

/** Every generated enum maps to the client enum of the same values; a missing entry fails to compile. */
const FEEDBACK_TYPES: Record<FileUploadFeedbackTypeEnum, FeedbackType> = {
    AUTOMATIC: FeedbackType.AUTOMATIC,
    MANUAL: FeedbackType.MANUAL,
    MANUAL_UNREFERENCED: FeedbackType.MANUAL_UNREFERENCED,
    AUTOMATIC_ADAPTED: FeedbackType.AUTOMATIC_ADAPTED,
};

const VISIBILITIES: Record<FileUploadFeedbackVisibilityEnum, Visibility> = {
    ALWAYS: Visibility.Always,
    AFTER_DUE_DATE: Visibility.AfterDueDate,
    NEVER: Visibility.Never,
};

/**
 * Converts a feedback of a file upload result into the feedback shape the shared {@code convertFeedbackFromServer} reads.
 *
 * @param feedback the generated feedback
 * @returns the same feedback with client enums
 */
export function toFeedbackFromServer(feedback: FileUploadFeedback): FeedbackDTO {
    return {
        id: feedback.id,
        text: feedback.text,
        detailText: feedback.detailText,
        hasLongFeedbackText: feedback.hasLongFeedbackText,
        reference: feedback.reference,
        credits: feedback.credits,
        positive: feedback.positive,
        type: feedback.type === undefined ? undefined : FEEDBACK_TYPES[feedback.type],
        visibility: feedback.visibility === undefined ? undefined : VISIBILITIES[feedback.visibility],
        gradingInstruction: feedback.gradingInstruction,
    };
}
