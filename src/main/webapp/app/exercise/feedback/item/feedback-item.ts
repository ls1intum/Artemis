import { FeedbackColor, FeedbackNode } from 'app/exercise/feedback/node/feedback-node';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';

export type FeedbackItemType = 'Test' | 'Static Code Analysis' | 'Reviewer' | 'Subsequent' | 'Submission Policy';

export interface FeedbackItemCodeReference {
    filePath: string;
    line: number;
    lineEnd?: number;
    lines?: FeedbackItemCodeReferenceLine[];
}

export interface FeedbackItemCodeReferenceLine {
    line: number;
    code: string;
    referenced: boolean;
}

export class FeedbackItem implements FeedbackNode {
    name!: string; // always provided when a FeedbackItem is built (see FeedbackItemService.createFeedbackItem)
    credits: number | undefined;
    maxCredits?: number;
    type!: FeedbackItemType; // always provided when a FeedbackItem is built (see FeedbackItemService.createFeedbackItem)
    title?: string; // this is typically feedback.text
    text?: string; // this is typically feedback.detailText
    positive?: boolean;
    color?: FeedbackColor;
    feedbackReference!: Feedback; // always provided when a FeedbackItem is built; has to be connected to a result
    codeReference?: FeedbackItemCodeReference;
}
