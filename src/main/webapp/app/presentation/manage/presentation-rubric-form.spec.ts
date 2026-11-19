import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';

import { PresentationRubricCriterion, PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { createCriterionForm, createLevelForms, createRubricForm, notBlank, readRubricForm, rubricTotalWeight } from 'app/presentation/manage/presentation-rubric-form';
import { rubricCriterion } from 'test/helpers/presentation-rubric-test-helpers';

const criteria: PresentationRubricCriterion[] = [
    rubricCriterion('Content', 50, PresentationRubricScale.FIVE_POINT, { id: 1, texts: true }),
    rubricCriterion('Delivery', 30, PresentationRubricScale.THREE_POINT, { id: 2, texts: true }),
    rubricCriterion('Slides', 20, PresentationRubricScale.FIVE_POINT, { id: 3, texts: true }),
];

describe('presentation rubric form', () => {
    it('should reject blank text but accept text and other values', () => {
        expect(notBlank(new FormControl('   '))).toEqual({ required: true });
        expect(notBlank(new FormControl('Content'))).toBeNull();
        expect(notBlank(new FormControl(3))).toBeNull();
    });

    it('should require at least one criterion', () => {
        const form = createRubricForm();

        expect(form.length).toBe(0);
        expect(form.hasError('rubricEmpty')).toBe(true);
    });

    it('should accept criteria whose weights add up to 100 percent and reject any other total', () => {
        const form = createRubricForm(criteria);
        expect(form.valid).toBe(true);
        expect(rubricTotalWeight(form)).toBe(100);

        form.at(2).controls.weight.setValue(10);

        expect(rubricTotalWeight(form)).toBe(90);
        expect(form.hasError('rubricWeightSum')).toBe(true);
    });

    it('should count a criterion without weight as zero', () => {
        const form = createRubricForm(criteria);

        form.at(0).controls.weight.setValue(undefined);

        expect(rubricTotalWeight(form)).toBe(50);
    });

    it('should fix the scales of a locked rubric only', () => {
        expect(createRubricForm(criteria, true).controls.every((criterionForm) => criterionForm.controls.scale.disabled)).toBe(true);
        expect(createRubricForm(criteria).controls.every((criterionForm) => criterionForm.controls.scale.enabled)).toBe(true);
    });

    it('should start a new criterion on the five-point scale without name and weight', () => {
        const criterionForm = createCriterionForm();

        expect(criterionForm.controls.scale.value).toBe(PresentationRubricScale.FIVE_POINT);
        expect(criterionForm.controls.levels.length).toBe(5);
        expect(criterionForm.controls.weight.value).toBeUndefined();
        expect(criterionForm.controls.name.hasError('required')).toBe(true);
        expect(criterionForm.controls.weight.hasError('required')).toBe(true);
    });

    it('should keep a missing id and weight undefined, also after a reset', () => {
        const criterionForm = createCriterionForm();
        expect(criterionForm.controls.id.value).toBeUndefined();
        criterionForm.controls.weight.setValue(40);

        criterionForm.reset();

        expect(criterionForm.controls.id.value).toBeUndefined();
        expect(criterionForm.controls.weight.value).toBeUndefined();
        expect(criterionForm.controls.levels.at(0).controls.id.value).toBeUndefined();
    });

    it('should reject a blank name and weights outside of 1 to 100', () => {
        const criterionForm = createCriterionForm({ name: '   ', weight: 0 });
        expect(criterionForm.controls.name.hasError('required')).toBe(true);
        expect(criterionForm.controls.weight.hasError('min')).toBe(true);

        criterionForm.controls.weight.setValue(101);

        expect(criterionForm.controls.weight.hasError('max')).toBe(true);
    });

    it('should build one level per position of the scale and keep the given levels', () => {
        const levels = createLevelForms(PresentationRubricScale.THREE_POINT, [{ id: 7, description: 'Unclear' }]);

        expect(levels.getRawValue()).toEqual([
            { id: 7, description: 'Unclear' },
            { id: undefined, description: '' },
            { id: undefined, description: '' },
        ]);
    });

    it('should read the criteria in their order with trimmed names and without empty texts', () => {
        const form = createRubricForm(criteria, true);
        form.at(0).controls.name.setValue('  Content  ');
        form.at(1).controls.description.setValue('');
        form.at(1).controls.levels.at(0).controls.description.setValue('');

        const read = readRubricForm(form);

        expect(read.map((readCriterion) => readCriterion.name)).toEqual(['Content', 'Delivery', 'Slides']);
        expect(read[1].description).toBeUndefined();
        expect(read[1].levels[0]).toEqual({ id: 21, description: undefined });
        // A locked scale is disabled but still part of what is saved
        expect(read.map((readCriterion) => readCriterion.scale)).toEqual(criteria.map((original) => original.scale));
        expect(read[2]).toEqual(criteria[2]);
    });
});
