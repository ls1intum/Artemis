import { FeedbackColor, FeedbackNode } from 'app/exercise/feedback/node/feedback-node';
import { Feedback } from 'app/entities/feedback.model';

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
    feedbackReference: Feedback; // has to be connected to a result
}
