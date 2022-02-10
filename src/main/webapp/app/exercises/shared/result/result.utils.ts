import { Result } from 'app/entities/result.model';
import { cloneDeep } from 'lodash-es';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

/**
 * Check if the given result was initialized and has a score
 *
 * @param result
 */
export const initializedResultWithScore = (result?: Result) => {
    return result != undefined && (result.score || result.score === 0);
};

/**
 * Prepare a result that contains a participation which is needed in the rating component
 */
export const addParticipationToResult = (result: Result | undefined, participation: StudentParticipation) => {
    const ratingResult = cloneDeep(result);
    if (ratingResult) {
        const ratingParticipation = cloneDeep(participation);
        // remove circular dependency
        ratingParticipation.exercise!.studentParticipations = [];
        ratingResult.participation = ratingParticipation;
    }
    return ratingResult;
};

/**
 * searches for all unreferenced feedback in an array of feedbacks of a result
 * @param feedbacks the feedback of a result
 * @returns an array with the unreferenced feedback of the result
 */
export const getUnreferencedFeedback = (feedbacks: Feedback[] | undefined): Feedback[] | undefined => {
    return feedbacks ? feedbacks.filter((feedbackElement) => !feedbackElement.reference && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED) : undefined;
};
