import { Injectable } from '@angular/core';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { Range } from 'app/shared/util/utils';

@Injectable({ providedIn: 'root' })
export class PlagiarismInspectorService {
    /**
     * Filters the given comparisons and returns only those that have a similarity within the range [minimumSimilarity, maximumSimilarity)
     * @param range the similarity range the comparisons should be filtered against
     * @param comparisons the comparisons that should be filtered
     */
    filterComparisons(range: Range, comparisons?: PlagiarismComparison<any>[]): PlagiarismComparison<any>[] {
        if (!comparisons) {
            return [];
        }
        let filterFunction;
        if (range.upperBound === 100) {
            filterFunction = (comparison: PlagiarismComparison<any>) => comparison.similarity >= range.lowerBound && comparison.similarity <= range.upperBound;
        } else {
            filterFunction = (comparison: PlagiarismComparison<any>) => comparison.similarity >= range.lowerBound && comparison.similarity < range.upperBound;
        }
        return comparisons.filter(filterFunction);
    }
}
