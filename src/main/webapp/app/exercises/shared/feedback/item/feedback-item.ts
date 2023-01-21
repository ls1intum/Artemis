import { FeedbackColor, FeedbackNode } from 'app/exercises/shared/feedback/node/feedback-node';

export type FeedbackItemType = 'Test' | 'Static Code Analysis' | 'Reviewer' | 'Subsequent' | 'Submission Policy';

export class FeedbackItem implements FeedbackNode {
    name: string;
    credits: number | undefined;
    maxCredits?: number;
    type: FeedbackItemType;
    title?: string; // this is typically feedback.text
    text?: string; // this is typically feedback.detailText
    positive?: boolean;
    color?: FeedbackColor;
}
