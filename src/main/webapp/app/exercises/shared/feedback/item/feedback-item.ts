export enum FeedbackItemType {
    Issue,
    Test,
    Feedback,
    Policy,
    Subsequent,
}

export class FeedbackItem {
    type: FeedbackItemType;
    category: string;
    previewText?: string; // used for long texts with line breaks
    title?: string; // this is typically feedback.text
    text?: string; // this is typically feedback.detailText
    positive?: boolean;
    credits?: number;
    actualCredits?: number;
}
