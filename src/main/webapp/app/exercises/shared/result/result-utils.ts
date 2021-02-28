import { Result } from 'app/entities/result.model';
import { cloneDeep } from 'lodash';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

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
