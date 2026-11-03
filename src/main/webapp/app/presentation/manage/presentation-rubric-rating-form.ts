import { FormArray, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import { PresentationRubricCriterion, PresentationRubricRating } from 'app/presentation/shared/entities/presentation-assessment.model';

export type RatingForm = FormGroup<{
    levelId: FormControl<number | null>;
    comment: FormControl<string>;
}>;
export type RatingsForm = FormArray<RatingForm>;

export function countUnrated(form: RatingsForm): number {
    return form.controls.filter((rating) => rating.controls.levelId.value === null).length;
}

/** A rubric is rated completely or not at all, and a saved rubric result can be changed but not removed. */
const ratingsValidator =
    (required: boolean): ValidatorFn =>
    (control): ValidationErrors | null => {
        const form = control as RatingsForm;
        const unrated = countUnrated(form);
        if (unrated === 0) {
            return null;
        }
        if (unrated < form.length) {
            return { ratingsIncomplete: true };
        }
        return required ? { ratingsRequired: true } : null;
    };

/** Builds one group of controls per criterion, in the order of the rubric, filled with the saved ratings. */
export function createRatingsForm(criteria: PresentationRubricCriterion[], ratings: PresentationRubricRating[] = [], required = false): RatingsForm {
    return new FormArray(
        criteria.map((criterion) => {
            const rating = ratings.find((candidate) => candidate.criterionId === criterion.id);
            return new FormGroup({
                // An unrated criterion has no level; null rather than undefined, which FormControl replaces with null anyway
                levelId: new FormControl<number | null>(rating?.levelId ?? null),
                comment: new FormControl(rating?.comment ?? '', { nonNullable: true, validators: [Validators.maxLength(1000)] }),
            });
        }),
        { validators: ratingsValidator(required) },
    );
}

/** The ratings to send, or none while no criterion is rated. */
export function readRatingsForm(criteria: PresentationRubricCriterion[], form: RatingsForm): PresentationRubricRating[] | undefined {
    const ratings = form.controls.flatMap((rating, index) => {
        const levelId = rating.controls.levelId.value;
        const criterionId = criteria[index]?.id;
        return levelId !== null && criterionId !== undefined ? [{ criterionId, levelId, comment: rating.controls.comment.value.trim() || undefined }] : [];
    });
    return ratings.length > 0 ? ratings : undefined;
}

/**
 * The rubric score in percent as the server calculates it, unrounded, or undefined while a criterion is unrated:
 * the lowest level counts nothing and the highest the full weight of its criterion.
 */
export function rubricPreviewScore(criteria: PresentationRubricCriterion[], form: RatingsForm): number | undefined {
    let score = 0;
    for (const [index, criterion] of criteria.entries()) {
        const position = criterion.levels.findIndex((level) => level.id === form.at(index)?.controls.levelId.value);
        if (position < 0) {
            return undefined;
        }
        score += (criterion.weight * position) / (criterion.levels.length - 1);
    }
    return score;
}
