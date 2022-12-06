import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

/**
 * Returns all FeedbackItemGroups for Programming exercises
 */
export const getAllFeedbackItemGroups = (exercise: Exercise): FeedbackItemGroup[] => {
    return [
        new FeedbackItemGroupWrong(),
        new FeedbackItemGroupWarning(exercise),
        new FeedbackItemGroupInfo(),
        new FeedbackItemGroupMissing(),
        new FeedbackItemGroupCorrect(exercise),
    ];
};

/**
 * Automated feedbacks with no influence on final score
 */
class FeedbackItemGroupMissing extends FeedbackItemGroup {
    constructor() {
        super();
        this.name = 'missing';
        this.color = 'var(--secondary)';
        this.open = true;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === 'Test' && (feedbackItem.credits === 0 || !feedbackItem.credits);
    }
}

/**
 * Negative feedbacks that are not SCA
 */
class FeedbackItemGroupWrong extends FeedbackItemGroup {
    constructor() {
        super();
        this.name = 'wrong';
        this.color = 'var(--danger)';
        this.open = true;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === 'Test' && feedbackItem.credits !== undefined && feedbackItem.credits < 0;
    }
}

/**
 * Negative feedbacks that are SCA
 */
class FeedbackItemGroupWarning extends FeedbackItemGroup {
    constructor(exercise: Exercise) {
        super();
        this.name = 'warning';
        this.color = 'var(--warning)';

        const programmingExercise = exercise as ProgrammingExercise;
        this.maxCredits = programmingExercise.maxStaticCodeAnalysisPenalty && programmingExercise.maxStaticCodeAnalysisPenalty * programmingExercise.maxPoints!;
        this.open = true;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === 'Static Code Analysis';
    }
}

/**
 * - Reviewer feedback with no influence on grade
 * - Subsequent feedback
 */
class FeedbackItemGroupInfo extends FeedbackItemGroup {
    constructor() {
        super();
        this.name = 'info';
        this.color = 'var(--info)';
        this.open = false;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isReviewerFeedback = feedbackItem.type === 'Reviewer' && (feedbackItem.credits === 0 || !feedbackItem.credits);
        const isSubsequentFeedback = feedbackItem.type === 'Subsequent';
        return isReviewerFeedback || isSubsequentFeedback;
    }
}

/**
 * - Positive credits from Reviewer
 * - Positive credits from Test cases
 */
class FeedbackItemGroupCorrect extends FeedbackItemGroup {
    constructor(exercise: Exercise) {
        super();
        this.name = 'correct';
        this.color = 'var(--success)';
        this.maxCredits = exercise.maxPoints! + (exercise.bonusPoints ?? 0);
        this.open = false;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isReviewerFeedback = feedbackItem.type === 'Reviewer' && feedbackItem.credits !== undefined && feedbackItem.credits > 0;
        const isTestFeedback = feedbackItem.type === 'Test' && feedbackItem.credits !== undefined && feedbackItem.credits > 0;
        return isReviewerFeedback || isTestFeedback;
    }
}
