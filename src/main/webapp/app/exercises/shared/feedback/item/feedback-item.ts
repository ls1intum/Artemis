import { FeedbackColor, FeedbackNode } from 'app/exercises/shared/feedback/node/feedback-node';
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
    feedback: FeedbackReference;
}

export class FeedbackReference {
    feedbackId: number;
    resultId: number;
    hasLongFeedback: boolean;

    public constructor(feedback: Feedback) {
        this.feedbackId = feedback.id!;
        this.resultId = feedback.result?.id ?? 0;
        this.hasLongFeedback = feedback.hasLongFeedbackText ?? false;
    }
}
