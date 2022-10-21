import { TestBed } from '@angular/core/testing';
import { PlagiarismInspectorService } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.service';
import { ArtemisTestModule } from '../test.module';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { Range } from 'app/shared/util/utils';

describe('PlagiarismInspectorService', () => {
    let service: PlagiarismInspectorService;

    let result: PlagiarismComparison<TextSubmissionElement>[];
    let range: Range;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(PlagiarismInspectorService);
            });
    });

    const comparison1 = { id: 1, similarity: 5 };
    const comparison2 = { id: 2, similarity: 16 };
    const comparison3 = { id: 3, similarity: 22 };
    const comparison4 = { id: 4, similarity: 35 };
    const comparison5 = { id: 5, similarity: 41 };
    const comparison6 = { id: 6, similarity: 59 };
    const comparison7 = { id: 7, similarity: 61 };
    const comparison8 = { id: 8, similarity: 72 };
    const comparison9 = { id: 9, similarity: 85 };
    const comparison10 = { id: 10, similarity: 97 };

    // values in order to test the interval borders
    const comparison11 = { id: 11, similarity: 0 };
    const comparison12 = { id: 12, similarity: 10 };
    const comparison13 = { id: 13, similarity: 20 };
    const comparison14 = { id: 14, similarity: 30 };
    const comparison15 = { id: 15, similarity: 40 };
    const comparison16 = { id: 16, similarity: 50 };
    const comparison17 = { id: 17, similarity: 60 };
    const comparison18 = { id: 18, similarity: 70 };
    const comparison19 = { id: 19, similarity: 80 };
    const comparison20 = { id: 20, similarity: 90 };
    const comparison21 = { id: 21, similarity: 100 };

    const comparisons = [
        comparison1,
        comparison2,
        comparison3,
        comparison4,
        comparison5,
        comparison6,
        comparison7,
        comparison8,
        comparison9,
        comparison10,
        comparison11,
        comparison12,
        comparison13,
        comparison14,
        comparison15,
        comparison16,
        comparison17,
        comparison18,
        comparison19,
        comparison20,
        comparison21,
    ] as PlagiarismComparison<TextSubmissionElement>[];

    const borderValues = [
        comparison11,
        comparison12,
        comparison13,
        comparison14,
        comparison15,
        comparison16,
        comparison17,
        comparison18,
        comparison19,
        comparison20,
        comparison21,
    ] as PlagiarismComparison<TextSubmissionElement>[];

    it.each([0, 10, 20, 30, 40, 50, 60, 70, 80])('should filter comparisons correctly for range < 100%', (minimumSimilarity: number) => {
        range = { lowerBound: minimumSimilarity, upperBound: minimumSimilarity + 10 };
        const index = Math.round(minimumSimilarity / 10);

        result = service.filterComparisons(range, comparisons);

        expect(result).toHaveLength(2);
        expect(result).toEqual([comparisons[index], borderValues[index]]);
    });

    it('should filter comparisons correctly for maximal range 100%', () => {
        range = { lowerBound: 90, upperBound: 100 };

        result = service.filterComparisons(range, comparisons);

        expect(result).toHaveLength(3);
        expect(result).toEqual([comparison10, comparison20, comparison21]);
    });

    it('should return empty array if comparisons are empty', () => {
        range = { lowerBound: 30, upperBound: 40 };

        result = service.filterComparisons(range, undefined);

        expect(result).toHaveLength(0);
    });
});
