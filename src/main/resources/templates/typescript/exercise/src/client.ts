const ITERATIONS = 10;
const DATES_LENGTH_MIN = 5;
const DATES_LENGTH_MAX = 15;

/**
 * Main function.
 * Add code to demonstrate your implementation here.
 */
function main() {
    // TODO: Init Context and Policy

    // Run multiple times to simulate different sorting strategies
    for (let i = 0; i < ITERATIONS; i++) {
        const dates = createRandomDates();

        // TODO: Configure context

        console.log('Unsorted Array of dates:');
        console.log(dates);

        // TODO: Sort dates

        console.log('Sorted Array of dates:');
        console.log(dates);
    }
}

/**
 * Generates an Array of random Date objects with random Array length between
 * {@link DATES_LENGTH_MIN} and {@link DATES_LENGTH_MAX}.
 *
 * @return an Array of random Date objects
 */
function createRandomDates(): Array<Date> {
    const length = randomIntegerWithin(DATES_LENGTH_MIN, DATES_LENGTH_MAX);

    const lowestDate = new Date('2024-09-15');
    const highestDate = new Date('2025-01-15');

    return Array.from(Array(length), () => randomDateWithin(lowestDate, highestDate));
}

/**
 * Creates a random Date within the given range.
 *
 * @param low {Date} the lower bound
 * @param high {Date} the upper bound
 * @return {Date} random Date within the given range
 */
function randomDateWithin(low: Date, high: Date): Date {
    const randomTimestamp = randomIntegerWithin(low.valueOf(), high.valueOf());
    return new Date(randomTimestamp);
}

/**
 * Creates a random int within the given range.
 *
 * @param low {number} the lower bound
 * @param high {number} the upper bound
 * @returns {number} random int within the given range
 */
function randomIntegerWithin(low: number, high: number): number {
    return Math.floor(Math.random() * (high - low + 1)) + low;
}

main();
