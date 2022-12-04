import { FeedbackItem } from 'app/exercises/shared/result/detail/result-detail.component';
import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';

/**
 * Returns all FeedbackItemGroups for Programming exercises
 */
export const getAllFeedbackItemGroups = (): FeedbackItemGroup[] => {
    return [
        new FeedbackItemGroupAll(),
        new FeedbackItemGroupWrong(),
        new FeedbackItemGroupWarning(),
        new FeedbackItemGroupInfo(),
        new FeedbackItemGroupMissing(),
        new FeedbackItemGroupCorrect(),
    ];
};

/**
 * Test class do not use
 * @deprecated remove this
 */
class FeedbackItemGroupAll extends FeedbackItemGroup {
    name = 'all';
    color = 'bg-light';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return true;
    }
}

/**
 * Automated feedbacks with no influence on final score
 */
class FeedbackItemGroupMissing extends FeedbackItemGroup {
    name = 'missing';
    color = 'bg-secondary';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return false;
    }
}

/**
 * Negative feedbacks that are not SCA
 */
class FeedbackItemGroupWrong extends FeedbackItemGroup {
    name = 'wrong';
    color = 'bg-danger';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return false;
    }
}

/**
 * Negative feedbacks that are SCA
 */
class FeedbackItemGroupWarning extends FeedbackItemGroup {
    name = 'warning';
    color = 'bg-warning';
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
    color = 'bg-info';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return false;
    }
}

/**
 * Positive impact on grade
 */
class FeedbackItemGroupCorrect extends FeedbackItemGroup {
    name = 'correct';
    color = 'bg-success';
    shouldContain(feedbackItem: FeedbackItem): boolean {
        return false;
    }
}
