/**
 * Small statistics helpers replacing the `simple-statistics` dependency for the few functions
 * Artemis used: arithmetic mean, median and population standard deviation. The implementations
 * mirror simple-statistics exactly so previously computed values do not change.
 */

/**
 * Sum of the given numbers using the Kahan-Babuska algorithm, which corrects for floating-point
 * round-off. This matches the summation simple-statistics uses for {@link mean}.
 */
function kahanBabuskaSum(values: number[]): number {
    if (values.length === 0) {
        return 0;
    }
    let sum = values[0];
    let correction = 0;
    for (let i = 1; i < values.length; i++) {
        const value = values[i];
        const transition = sum + value;
        if (Math.abs(sum) >= Math.abs(value)) {
            correction += sum - transition + value;
        } else {
            correction += value - transition + sum;
        }
        sum = transition;
    }
    return sum + correction;
}

/**
 * The arithmetic mean (average) of one or more data points.
 * @throws Error if the input is empty (matching simple-statistics).
 */
export function mean(values: number[]): number {
    if (values.length === 0) {
        throw new Error('mean requires at least one data point');
    }
    return kahanBabuskaSum(values) / values.length;
}

/**
 * The median of one or more data points. Uses the linear-interpolation quantile at 0.5 (numpy/R
 * type 7), which is the middle value for an odd-length input and the average of the two middle
 * values for an even-length input.
 * @throws Error if the input is empty (matching simple-statistics).
 */
export function median(values: number[]): number {
    if (values.length === 0) {
        throw new Error('median requires at least one data point');
    }
    const sorted = values.slice().sort((a, b) => a - b);
    const index = (sorted.length - 1) / 2;
    const lower = Math.floor(index);
    const upper = Math.ceil(index);
    if (lower === upper) {
        return sorted[lower];
    }
    return sorted[lower] + (index - lower) * (sorted[upper] - sorted[lower]);
}

/**
 * The population standard deviation (square root of the population variance, dividing by n). Returns
 * 0 for a single data point, matching simple-statistics.
 * @throws Error if the input is empty (matching simple-statistics).
 */
export function standardDeviation(values: number[]): number {
    if (values.length === 0) {
        throw new Error('standardDeviation requires at least one data point');
    }
    if (values.length === 1) {
        return 0;
    }
    const meanValue = mean(values);
    let sumOfSquaredDeviations = 0;
    for (const value of values) {
        const deviation = value - meanValue;
        sumOfSquaredDeviations += deviation * deviation;
    }
    return Math.sqrt(sumOfSquaredDeviations / values.length);
}
