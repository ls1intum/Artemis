import { FeedbackGroup } from 'app/exercises/shared/feedback/group/feedback-group';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

/**
 * Returns all FeedbackItemGroups for Programming exercises in the order, in which they will be displayed
 */
export const getAllFeedbackGroups = (exercise: Exercise): FeedbackGroup[] => {
    return [new ProgrammingFeedbackGroupWrong(), new ProgrammingFeedbackGroupWarning(exercise), new ProgrammingFeedbackGroupInfo(), new ProgrammingFeedbackGroupCorrect()];
};

/**
 * Negative feedbacks that are not SCA
 */
class ProgrammingFeedbackGroupWrong extends FeedbackGroup {
    constructor() {
        super();
        this.name = 'wrong';
        this.color = 'danger';
        this.open = true;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isReviewerFeedback = feedbackItem.type === 'Reviewer' && feedbackItem.credits !== undefined && feedbackItem.credits < 0;
        const isTestFeedback = feedbackItem.type === 'Test';
        const isFailedTest = feedbackItem.positive === false || (feedbackItem.positive === undefined && feedbackItem.credits === 0);
        return isReviewerFeedback || (isTestFeedback && isFailedTest);
    }
}

/**
 * - Negative feedbacks that are SCA
 * - Submission policy
 */
class ProgrammingFeedbackGroupWarning extends FeedbackGroup {
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
class ProgrammingFeedbackGroupInfo extends FeedbackGroup {
    constructor() {
        super();
        this.name = 'info';
        this.color = 'info';
        this.open = false;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isReviewerFeedback = feedbackItem.type === 'Reviewer' && feedbackItem.credits === 0;
        const isSubsequentFeedback = feedbackItem.type === 'Subsequent';
        return isReviewerFeedback || isSubsequentFeedback;
    }
}

/**
 * - Positive from Reviewer
 * - Positive from Test cases
 */
class ProgrammingFeedbackGroupCorrect extends FeedbackGroup {
    constructor() {
        super();
        this.name = 'correct';
        this.color = 'success';
        this.open = false;
    }

    shouldContain(feedbackItem: FeedbackItem): boolean {
        const isReviewerFeedback = feedbackItem.type === 'Reviewer' && feedbackItem.credits !== undefined && feedbackItem.credits > 0;
        const isTestFeedback = feedbackItem.type === 'Test';
        const isSuccessfulTest = feedbackItem.positive === true || (feedbackItem.positive === undefined && !!feedbackItem.credits);
        return isReviewerFeedback || (isTestFeedback && isSuccessfulTest);
    }
}
