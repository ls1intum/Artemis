import { describe, expect, it } from 'vitest';

import { PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { rubricLevelCount, rubricLevelKey } from 'app/presentation/shared/presentation-rubric.util';

describe('presentation rubric util', () => {
    it('should count the levels of each scale', () => {
        expect(rubricLevelCount(PresentationRubricScale.THREE_POINT)).toBe(3);
        expect(rubricLevelCount(PresentationRubricScale.FIVE_POINT)).toBe(5);
    });

    it.each([
        { scale: PresentationRubricScale.THREE_POINT, keys: ['poor', 'satisfactory', 'excellent'] },
        { scale: PresentationRubricScale.FIVE_POINT, keys: ['poor', 'needsImprovement', 'satisfactory', 'good', 'excellent'] },
    ])('should name the levels of the $scale scale from the lowest up', ({ scale, keys }) => {
        expect(keys.map((_, position) => rubricLevelKey(scale, position))).toEqual(keys.map((key) => `artemisApp.presentationAssessment.rubric.levelKey.${key}`));
    });
});
