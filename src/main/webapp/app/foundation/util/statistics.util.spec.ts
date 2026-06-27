import { describe, expect, it } from 'vitest';
import { mean, median, standardDeviation } from 'app/foundation/util/statistics.util';

describe('statistics.util', () => {
    describe('mean', () => {
        it('computes the arithmetic mean', () => {
            expect(mean([0, 10])).toBe(5);
            expect(mean([2, 4, 4, 4, 5, 5, 7, 9])).toBe(5);
            expect(mean([1, 2, 3, 4])).toBe(2.5);
        });

        it('handles a single value', () => {
            expect(mean([42])).toBe(42);
        });

        it('handles negative and decimal values', () => {
            expect(mean([-1, 1])).toBe(0);
            expect(mean([1.5, 2.5])).toBe(2);
        });

        it('throws on empty input', () => {
            expect(() => mean([])).toThrow();
        });
    });

    describe('median', () => {
        it('returns the middle value for odd-length input', () => {
            expect(median([1, 2, 3])).toBe(2);
            expect(median([3, 1, 2])).toBe(2);
        });

        it('averages the two middle values for even-length input', () => {
            expect(median([1, 2, 3, 4])).toBe(2.5);
            expect(median([10, 2, 5, 100, 2, 1])).toBe(3.5);
        });

        it('handles a single value', () => {
            expect(median([7])).toBe(7);
        });

        it('does not mutate the input array', () => {
            const input = [3, 1, 2];
            median(input);
            expect(input).toEqual([3, 1, 2]);
        });

        it('throws on empty input', () => {
            expect(() => median([])).toThrow();
        });
    });

    describe('standardDeviation', () => {
        it('computes the population standard deviation (divides by n)', () => {
            // variance([2,4,4,4,5,5,7,9]) === 4 -> sd === 2
            expect(standardDeviation([2, 4, 4, 4, 5, 5, 7, 9])).toBe(2);
        });

        it('returns 0 when all values are identical', () => {
            expect(standardDeviation([5, 5, 5, 5])).toBe(0);
        });

        it('returns 0 for a single value', () => {
            expect(standardDeviation([5])).toBe(0);
        });

        it('matches a hand-computed value', () => {
            // population variance of [1,2,3,4,5] = 2 -> sd = sqrt(2)
            expect(standardDeviation([1, 2, 3, 4, 5])).toBeCloseTo(Math.sqrt(2), 12);
        });

        it('throws on empty input', () => {
            expect(() => standardDeviation([])).toThrow();
        });
    });
});
