import { FeedbackItemNode } from 'app/exercises/shared/feedback/item/feedback-item-node';

export type FeedbackItemType = 'Test' | 'Static Code Analysis' | 'Reviewer' | 'Subsequent' | 'Feedback' | 'Submission Policy';

export class FeedbackItem implements FeedbackItemNode {
    name: string; // TODO: this and type are mostly duplicated
    credits: number | undefined;
    type: FeedbackItemType;
    category: string;
    title?: string; // this is typically feedback.text
    text?: string; // this is typically feedback.detailText
    positive?: boolean;
    actualCredits?: number;
}
