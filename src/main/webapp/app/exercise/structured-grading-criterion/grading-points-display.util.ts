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
