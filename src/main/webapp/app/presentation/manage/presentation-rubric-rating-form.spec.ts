import { describe, expect, it } from 'vitest';

import { PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { RatingsForm, countUnrated, createRatingsForm, readRatingsForm, rubricPreviewScore } from 'app/presentation/manage/presentation-rubric-rating-form';
import { rubricCriterion } from 'test/helpers/presentation-rubric-test-helpers';

const criteria = [
    rubricCriterion('Criterion 1', 50, PresentationRubricScale.FIVE_POINT, { id: 1 }),
    rubricCriterion('Criterion 2', 30, PresentationRubricScale.THREE_POINT, { id: 2 }),
    rubricCriterion('Criterion 3', 20, PresentationRubricScale.FIVE_POINT, { id: 3 }),
];

/** Rates every criterion at the given position, counting from 0 at the lowest level. */
function rate(form: RatingsForm, ...positions: number[]): void {
    positions.forEach((position, index) => form.at(index).controls.levelId.setValue(criteria[index].levels[position].id));
}

describe('presentation rubric rating form', () => {
    it('should start unrated and fill in the saved ratings', () => {
        const form = createRatingsForm(criteria, [{ criterionId: 2, levelId: 23, comment: 'Engaging' }]);

        expect(form.getRawValue()).toEqual([
            { levelId: undefined, comment: '' },
            { levelId: 23, comment: 'Engaging' },
            { levelId: undefined, comment: '' },
        ]);
        expect(countUnrated(form)).toBe(2);
    });

    it('should accept no ratings and all ratings but not some of them', () => {
        const form = createRatingsForm(criteria);
        expect(form.valid).toBe(true);

        form.at(0).controls.levelId.setValue(11);
        expect(form.hasError('ratingsIncomplete')).toBe(true);

        rate(form, 3, 2, 1);
        expect(form.valid).toBe(true);
    });

    it('should require all ratings once a rubric result was saved', () => {
        const form = createRatingsForm(criteria, [], true);

        expect(form.hasError('ratingsRequired')).toBe(true);

        rate(form, 0, 0, 0);
        expect(form.valid).toBe(true);
    });

    it('should reject a comment longer than 1000 characters', () => {
        const form = createRatingsForm(criteria);

        form.at(0).controls.comment.setValue('a'.repeat(1001));

        expect(form.at(0).controls.comment.hasError('maxlength')).toBe(true);
    });

    it('should read only rated criteria with trimmed comments', () => {
        const form = createRatingsForm(criteria);
        expect(readRatingsForm(criteria, form)).toBeUndefined();

        rate(form, 3, 2, 1);
        form.at(0).controls.comment.setValue('  Clear structure  ');
        form.at(1).controls.comment.setValue('   ');

        expect(readRatingsForm(criteria, form)).toEqual([
            { criterionId: 1, levelId: 14, comment: 'Clear structure' },
            { criterionId: 2, levelId: 23, comment: undefined },
            { criterionId: 3, levelId: 32, comment: undefined },
        ]);
    });

    it('should calculate the score as the server does once every criterion is rated', () => {
        const form = createRatingsForm(criteria);
        rate(form, 3, 2);
        expect(rubricPreviewScore(criteria, form)).toBeUndefined();

        form.at(2).controls.levelId.setValue(32);

        // score = Σ weight · position / (level count − 1), with position 0 for the lowest level
        //       = 50 % · 3/4 + 30 % · 2/2 + 20 % · 1/4 = 72.5 %
        // The same example pins the server calculation in PresentationGradingMethodIntegrationTest
        expect(rubricPreviewScore(criteria, form)).toBe(72.5);
    });
});
