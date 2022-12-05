/**
 * @deprecated use {@see TFeedbackItemType} and rename after this is deleted
 */
export enum FeedbackItemType {
    Issue,
    Test,
    Feedback,
    Policy,
    Subsequent,
}

export type TFeedbackItemType = 'Test' | 'Static Code Analysis' | 'Reviewer' | 'Subsequent';

export class FeedbackItem {
    type?: FeedbackItemType;
    tType?: TFeedbackItemType; // TODO: shouldn't be optional
    category: string;
    previewText?: string; // used for long texts with line breaks
    title?: string; // this is typically feedback.text
    text?: string; // this is typically feedback.detailText
    positive?: boolean;
    credits?: number;
    actualCredits?: number;
}
