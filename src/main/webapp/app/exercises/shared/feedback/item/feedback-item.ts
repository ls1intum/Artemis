import { FeedbackItemNode } from 'app/exercises/shared/feedback/item/feedback-item-node';

export type FeedbackItemType = 'Test' | 'Static Code Analysis' | 'Reviewer' | 'Subsequent' | 'Feedback' | 'Submission Policy';

export class FeedbackItem implements FeedbackItemNode {
    name: string;
    credits: number | undefined;
    maxCredits?: number;
    type: FeedbackItemType;
    title?: string; // this is typically feedback.text
    text?: string; // this is typically feedback.detailText
    positive?: boolean;
}
