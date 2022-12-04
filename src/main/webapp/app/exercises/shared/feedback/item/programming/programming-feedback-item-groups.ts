import { FeedbackItem } from 'app/exercises/shared/result/detail/result-detail.component';
import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';

/**
 * Returns all FeedbackItemGroups for Programming exercises
 */
export const getAllFeedbackItemGroups = (): FeedbackItemGroup[] => {
    return [
        new FeedbackItemGroupAll(),
        new FeedbackItemGroupMissing(),
        new FeedbackItemGroupWrong(),
        new FeedbackItemGroupWarning(),
        new FeedbackItemGroupInfo(),
        new FeedbackItemGroupCorrect(),
    ];
};

/**
 * Test class do not use
 * @deprecated remove this
 */
class FeedbackItemGroupAll extends FeedbackItemGroup {
    name = 'all';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return true;
    }
}

/**
 * Automated feedbacks with no influence on final score
 */
class FeedbackItemGroupMissing extends FeedbackItemGroup {
    name = 'missing';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return false;
    }
}

/**
 * Negative feedbacks that are not SCA
 */
class FeedbackItemGroupWrong extends FeedbackItemGroup {
    name = 'wrong';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return false;
    }
}

/**
 * Negative feedbacks that are SCA
 */
class FeedbackItemGroupWarning extends FeedbackItemGroup {
    name = 'warning';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return false;
    }
}

/**
 * - Reviewer feedback with no influence on grade
 * - Subsequent feedback
 */
class FeedbackItemGroupInfo extends FeedbackItemGroup {
    name = 'info';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return false;
    }
}

/**
 * Positive impact on grade
 */
class FeedbackItemGroupCorrect extends FeedbackItemGroup {
    name = 'correct';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return false;
    }
}
