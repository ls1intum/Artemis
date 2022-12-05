import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';

/**
 * Returns all FeedbackItemGroups for Programming exercises
 */
export const getAllFeedbackItemGroups = (): FeedbackItemGroup[] => {
    return [new FeedbackItemGroupWrong(), new FeedbackItemGroupWarning(), new FeedbackItemGroupInfo(), new FeedbackItemGroupMissing(), new FeedbackItemGroupCorrect()];
};

/**
 * Automated feedbacks with no influence on final score
 */
class FeedbackItemGroupMissing extends FeedbackItemGroup {
    name = 'missing';
    open = true;
    color = 'var(--secondary)';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === 'Test' && (feedbackItem.credits === 0 || !feedbackItem.credits);
    }
}

/**
 * Negative feedbacks that are not SCA
 */
class FeedbackItemGroupWrong extends FeedbackItemGroup {
    name = 'wrong';
    open = true;
    color = 'var(--danger)';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === 'Test' && feedbackItem.credits !== undefined && feedbackItem.credits < 0;
    }
}

/**
 * Negative feedbacks that are SCA
 */
class FeedbackItemGroupWarning extends FeedbackItemGroup {
    name = 'warning';
    open = true;
    color = 'var(--warning)';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === 'Static Code Analysis';
    }
}

/**
 * - Reviewer feedback with no influence on grade
 * - Subsequent feedback
 */
class FeedbackItemGroupInfo extends FeedbackItemGroup {
    name = 'info';
    color = 'var(--info)';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isReviewerFeedback = feedbackItem.type === 'Reviewer' && (feedbackItem.credits === 0 || !feedbackItem.credits);
        const isSubsequentFeedback = feedbackItem.type === 'Subsequent';
        return isReviewerFeedback || isSubsequentFeedback;
    }
}

/**
 * Positive impact on grade
 */
class FeedbackItemGroupCorrect extends FeedbackItemGroup {
    name = 'correct';
    color = 'var(--success)';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isReviewerFeedback = feedbackItem.type === 'Reviewer' && feedbackItem.credits !== undefined && feedbackItem.credits > 0;
        const isTestFeedback = feedbackItem.type === 'Test' && feedbackItem.credits !== undefined && feedbackItem.credits > 0;
        return isReviewerFeedback || isTestFeedback;
    }
}
