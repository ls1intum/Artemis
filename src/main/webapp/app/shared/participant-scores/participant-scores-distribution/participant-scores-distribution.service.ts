import { Injectable } from '@angular/core';
import { GradingInterval } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';

@Injectable({ providedIn: 'root' })
export class ParticipantScoresDistributionService {
    /**
     * Auxiliary method that checks if any of the given intervals is containing the given score
     * @param score the score that should be contained by an interval
     * @param intervals the passed intervals
     * @private
     */
    public isContainingIntervalPresent(score: number, intervals: GradingInterval[]): boolean {
        return intervals.some((interval) => {
            return this.scoreIsWithinIntervalBoundaries(score, interval) || this.scoreIsIncludedBoundary(score, interval);
        });
    }

    private scoreIsWithinIntervalBoundaries(score: number, interval: GradingInterval) {
        return score > interval.lowerBoundary && score < interval.upperBoundary;
    }

    private scoreIsIncludedBoundary(score: number, interval: GradingInterval) {
        return (interval.lowerBoundaryInclusive && score === interval.lowerBoundary) || (interval.upperBoundaryInclusive && score === interval.upperBoundary);
    }
}
