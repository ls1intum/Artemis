/** The five ordered steps of the rating scale, from no understanding to full understanding; each one has a theme colour `--rating-scale-<step>`. */
export type RatingSeverity = 'lowest' | 'low' | 'neutral' | 'high' | 'highest';

/**
 * Maps a rating onto the five steps of the rating scale. The ends of the scale are always `lowest` and `highest`,
 * its middle is `neutral`, and the steps next to the middle are `low` and `high`; anything further out joins the
 * ends. On a five-point scale that is one rating per step; on a seven-point scale the two lowest ratings are
 * `lowest` and the two highest `highest`.
 * @param score the rating, from 1 to `points`; ratings outside the scale are clamped onto it
 * @param points the number of points of the Likert scale
 */
export function ratingSeverity(score: number, points: number): RatingSeverity {
    if (points <= 1 || !Number.isFinite(score)) {
        return 'neutral';
    }
    const rating = Math.min(Math.max(Math.round(score), 1), points);
    if (rating === 1) {
        return 'lowest';
    }
    if (rating === points) {
        return 'highest';
    }
    const distanceFromMiddle = rating - (points + 1) / 2;
    if (distanceFromMiddle <= -2) {
        return 'lowest';
    }
    if (distanceFromMiddle < 0) {
        return 'low';
    }
    if (distanceFromMiddle === 0) {
        return 'neutral';
    }
    if (distanceFromMiddle < 2) {
        return 'high';
    }
    return 'highest';
}

/**
 * Formats a remaining time as `m:ss`, the way a countdown reads it: whole seconds, never negative, the minutes without a leading zero.
 * @param seconds the remaining seconds; fractions are floored, and anything below zero or not a number reads as `0:00`
 */
export function formatCountdown(seconds: number): string {
    const total = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0;
    const minutes = Math.floor(total / 60);
    const remainder = total % 60;
    return `${minutes}:${remainder.toString().padStart(2, '0')}`;
}
