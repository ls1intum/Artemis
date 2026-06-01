export const filterNaN = (input: number): number => (isNaN(input) ? 0 : input);

/**
 * Converts a value/max pair into a percentage clamped to [0, 100], returning 0 for non-finite
 * results (e.g. a zero or missing denominator). Mirrors ng-bootstrap's NgbProgressbar, which
 * clamped the bar fill to [0, 100]; used for the p-progressbar `[value]` bindings in the metrics
 * blocks so the bar can never overflow or render a `NaN%`/`Infinity%` width.
 */
export const toPercentage = (value: number, max: number): number => {
    const percentage = (100 * value) / max;
    return Number.isFinite(percentage) ? Math.min(100, Math.max(0, percentage)) : 0;
};
