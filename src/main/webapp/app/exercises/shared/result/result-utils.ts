import { Result } from 'app/entities/result.model';

/**
 * Check if the given result was initialized and has a score
 *
 * @param result
 */
export const initializedResultWithScore = (result?: Result) => {
    return result != undefined && (result.score || result.score === 0);
};
