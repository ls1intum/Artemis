export type FeedbackItemType = 'Test' | 'Static Code Analysis' | 'Reviewer' | 'Subsequent' | 'Feedback' | 'Submission Policy';

export class FeedbackItem {
    type: FeedbackItemType; // TODO: shouldn't be optional
    category: string;
    title?: string; // this is typically feedback.text
    text?: string; // this is typically feedback.detailText
    positive?: boolean;
    credits: number | undefined;
    actualCredits?: number;
}
