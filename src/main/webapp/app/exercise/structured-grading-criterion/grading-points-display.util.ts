import { TumUiTagSeverity } from '@tumaet/ui-angular';

/**
 * Presentation helpers shared by the structured grading instruction panel and the feedback cards, so a point
 * value looks identical in both places: a green pill when it awards points, red when it deducts, neutral at zero.
 */

/** Tag severity for a point value: `success` (awarded), `danger` (deducted), or `secondary` (neutral / zero). */
export function pointsSeverity(credits: number | undefined): TumUiTagSeverity {
    const value = credits ?? 0;
    if (value > 0) {
        return 'success';
    }
    if (value < 0) {
        return 'danger';
    }
    return 'secondary';
}

/** Signed, compact point label for a pill, e.g. `+10`, `-5`, `0` (the minus sign is part of a negative number). */
export function pointsLabel(credits: number | undefined): string {
    const value = credits ?? 0;
    return value > 0 ? `+${value}` : `${value}`;
}

/** Points are graded in half steps throughout Artemis, so every point stepper uses the same one. */
export const CREDITS_STEP = 0.5;

/** Normalizes typed points to the same half-point grid used by the steppers. */
export function normalizedCredits(credits: number | null | undefined): number | undefined {
    if (credits === null || credits === undefined || !Number.isFinite(credits)) {
        return undefined;
    }
    return Math.round(credits / CREDITS_STEP) * CREDITS_STEP;
}

/**
 * Applies one step of the point stepper. A hand-typed value is snapped onto the half-point grid in the direction of
 * travel first, so stepping up from 1.3 lands on 1.5 (the next grid point) rather than skipping to 2.
 * @param credits the current point value
 * @param delta the signed step to apply
 */
export function steppedCredits(credits: number | undefined, delta: number): number {
    const base = credits ?? 0;
    const snapped = (delta > 0 ? Math.floor(base / CREDITS_STEP) : Math.ceil(base / CREDITS_STEP)) * CREDITS_STEP;
    return snapped + delta;
}
