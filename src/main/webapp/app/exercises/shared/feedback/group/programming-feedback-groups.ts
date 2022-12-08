import { FeedbackGroup } from 'app/exercises/shared/feedback/group/feedback-group';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

/**
 * Returns all FeedbackItemGroups for Programming exercises in the order, in which they will be displayed
 */
export const getAllFeedbackGroups = (exercise: Exercise): FeedbackGroup[] => {
    return [new FeedbackGroupWrong(), new FeedbackGroupWarning(exercise), new FeedbackGroupInfo(), new FeedbackGroupMissing(), new FeedbackGroupCorrect(exercise)];
};

/**
 * Automated feedbacks with no influence on final score
 */
class FeedbackGroupMissing extends FeedbackGroup {
    constructor() {
        super();
        this.name = 'missing';
        this.color = 'secondary';
        this.open = true;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === 'Test' && (feedbackItem.credits === 0 || !feedbackItem.credits);
    }
}

/**
 * Negative feedbacks that are not SCA
 */
class FeedbackGroupWrong extends FeedbackGroup {
    constructor() {
        super();
        this.name = 'wrong';
        this.color = 'danger';
        this.open = true;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isReviewerFeedback = feedbackItem.type === 'Reviewer' && feedbackItem.credits && feedbackItem.credits < 0;
        const isTestFeedback = feedbackItem.type === 'Test' && feedbackItem.credits !== undefined && feedbackItem.credits < 0;
        return isReviewerFeedback || isTestFeedback;
    }
}

/**
 * - Negative feedbacks that are SCA
 * - Submission policy
 */
class FeedbackGroupWarning extends FeedbackGroup {
    constructor(exercise: Exercise) {
        super();
        this.name = 'warning';
        this.color = 'warning';

        const programmingExercise = exercise as ProgrammingExercise;
        this.maxCredits = programmingExercise.maxStaticCodeAnalysisPenalty && programmingExercise.maxStaticCodeAnalysisPenalty * programmingExercise.maxPoints!;
        this.open = true;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === 'Static Code Analysis' || feedbackItem.type === 'Submission Policy';
    }
}

/**
 * - Reviewer feedback with no influence on grade
 * - Subsequent feedback
 */
class FeedbackGroupInfo extends FeedbackGroup {
    constructor() {
        super();
        this.name = 'info';
        this.color = 'info';
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
class FeedbackGroupCorrect extends FeedbackGroup {
    constructor(exercise: Exercise) {
        super();
        this.name = 'correct';
        this.color = 'success';
        this.maxCredits = exercise.maxPoints! + (exercise.bonusPoints ?? 0);
        this.open = false;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isReviewerFeedback = feedbackItem.type === 'Reviewer' && feedbackItem.credits !== undefined && feedbackItem.credits > 0;
        const isTestFeedback = feedbackItem.type === 'Test' && feedbackItem.credits !== undefined && feedbackItem.credits > 0;
        return isReviewerFeedback || isTestFeedback;
    }
}
