import { Injectable } from '@angular/core';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { SimilarityRange } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-run-details.component';

@Injectable({ providedIn: 'root' })
export class PlagiarismInspectorService {
    filterComparisons(range: SimilarityRange, comparisons?: PlagiarismComparison<any>[]): PlagiarismComparison<any>[] {
        if (!comparisons) {
            return [];
        }
        let filterFunction;
        if (range.maximumSimilarity === 100) {
            filterFunction = (comparison: PlagiarismComparison<any>) => comparison.similarity >= range.minimumSimilarity && comparison.similarity <= range.maximumSimilarity;
        } else {
            filterFunction = (comparison: PlagiarismComparison<any>) => comparison.similarity >= range.minimumSimilarity && comparison.similarity < range.maximumSimilarity;
        }
        return comparisons.filter(filterFunction);
    }
}
