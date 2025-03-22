import { FeedbackGroup } from 'app/exercise/feedback/group/feedback-group';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';

/**
 * Returns all FeedbackItemGroups for unspecific exercise types in the order, in which they will be displayed
 */
export const getAllFeedbackGroups = (): FeedbackGroup[] => {
    return [new FeedbackGroupWrong(), new FeedbackGroupInfo(), new FeedbackGroupCorrect()];
};

/**
 * Negative credits
 */
class FeedbackGroupWrong extends FeedbackGroup {
    constructor() {
        super();
        this.name = 'wrong';
        this.color = 'danger';
        this.open = true;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isNotSubsequentFeedback = feedbackItem.type !== 'Subsequent';
        return feedbackItem.credits !== undefined && feedbackItem.credits < 0 && isNotSubsequentFeedback;
    }
}

/**
 * Credits are 0
 */
class FeedbackGroupInfo extends FeedbackGroup {
    constructor() {
        super();
        this.name = 'info';
        this.color = 'info';
        this.open = false;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isSubsequentFeedback = feedbackItem.type === 'Subsequent';
        return (feedbackItem.credits !== undefined && feedbackItem.credits === 0) || isSubsequentFeedback;
    }
}

/**
 * Positive credits
 */
class FeedbackGroupCorrect extends FeedbackGroup {
    constructor() {
        super();
        this.name = 'correct';
        this.color = 'success';
        this.open = false;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isNotSubsequentFeedback = feedbackItem.type !== 'Subsequent';
        return feedbackItem.credits !== undefined && feedbackItem.credits > 0 && isNotSubsequentFeedback;
    }
}
