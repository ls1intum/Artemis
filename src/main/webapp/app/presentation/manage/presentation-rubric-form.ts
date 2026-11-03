import { AbstractControl, FormArray, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import { PresentationRubricCriterion, PresentationRubricLevel, PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { RUBRIC_WEIGHT_SUM, rubricLevelCount } from 'app/presentation/shared/presentation-rubric.util';

export type RubricLevelForm = FormGroup<{
    id: FormControl<number | undefined>;
    description: FormControl<string>;
}>;

export type RubricCriterionForm = FormGroup<{
    id: FormControl<number | undefined>;
    name: FormControl<string>;
    description: FormControl<string>;
    weight: FormControl<number | null>;
    scale: FormControl<PresentationRubricScale>;
    levels: FormArray<RubricLevelForm>;
}>;

export type RubricForm = FormArray<RubricCriterionForm>;

export const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
    typeof control.value === 'string' && control.value.trim().length === 0 ? { required: true } : null;

/** A rubric needs at least one criterion, and the weights of its criteria add up to exactly 100 percent. */
const rubricValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const form = control as RubricForm;
    if (form.length === 0) {
        return { rubricEmpty: true };
    }
    return rubricTotalWeight(form) === RUBRIC_WEIGHT_SUM ? null : { rubricWeightSum: true };
};

export function rubricTotalWeight(form: RubricForm): number {
    return form.controls.reduce((sum, criterion) => sum + (criterion.controls.weight.value ?? 0), 0);
}

/** Builds the form controls of a rubric; a locked rubric keeps the scale of its criteria. */
export function createRubricForm(criteria: PresentationRubricCriterion[] = [], locked = false): RubricForm {
    return new FormArray(
        criteria.map((criterion) => createCriterionForm(criterion, locked)),
        { validators: rubricValidator },
    );
}

/** Builds the form controls of a criterion; a new criterion starts with the 5-point scale. */
export function createCriterionForm(criterion?: Partial<PresentationRubricCriterion>, locked = false): RubricCriterionForm {
    const scale = criterion?.scale ?? PresentationRubricScale.FIVE_POINT;
    return new FormGroup({
        id: new FormControl<number | undefined>(criterion?.id, { nonNullable: true }),
        name: new FormControl(criterion?.name ?? '', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(255)] }),
        description: new FormControl(criterion?.description ?? '', { nonNullable: true, validators: [Validators.maxLength(1000)] }),
        weight: new FormControl<number | null>(criterion?.weight ?? null, [Validators.required, Validators.min(1), Validators.max(RUBRIC_WEIGHT_SUM)]),
        scale: new FormControl({ value: scale, disabled: locked }, { nonNullable: true }),
        levels: createLevelForms(scale, criterion?.levels),
    });
}

/** Builds one group of controls per level of the scale, from the lowest level up; levels missing in the given list start empty. */
export function createLevelForms(scale: PresentationRubricScale, levels: PresentationRubricLevel[] = []): FormArray<RubricLevelForm> {
    return new FormArray(
        Array.from(
            { length: rubricLevelCount(scale) },
            (_, position) =>
                new FormGroup({
                    id: new FormControl<number | undefined>(levels[position]?.id, { nonNullable: true }),
                    description: new FormControl(levels[position]?.description ?? '', { nonNullable: true, validators: [Validators.maxLength(1000)] }),
                }),
        ),
    );
}

/** Reads the criteria in the order of the form, which is their position in the rubric. */
export function readRubricForm(form: RubricForm): PresentationRubricCriterion[] {
    return form.getRawValue().map((criterion) => ({
        id: criterion.id,
        name: criterion.name.trim(),
        description: criterion.description || undefined,
        weight: criterion.weight ?? 0,
        scale: criterion.scale,
        levels: criterion.levels.map((level) => ({ id: level.id, description: level.description || undefined })),
    }));
}
