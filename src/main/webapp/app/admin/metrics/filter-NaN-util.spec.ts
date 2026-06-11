/**
 * Vitest tests for filterNaN utility.
 */
import { describe, expect, it } from 'vitest';
import { filterNaN, toPercentage } from 'app/admin/metrics/filterNaN-util';

describe('filterNaN', () => {
    it('should return 0 for NaN', () => {
        expect(filterNaN(NaN)).toBe(0);
    });

    it('should return number for a number', () => {
        expect(filterNaN(12345)).toBe(12345);
    });
});

describe('toPercentage', () => {
    it('computes value/max as a percentage', () => {
        expect(toPercentage(25, 100)).toBe(25);
        expect(toPercentage(1, 4)).toBe(25);
    });

    it('clamps the result to the [0, 100] range', () => {
        expect(toPercentage(150, 100)).toBe(100);
        expect(toPercentage(-5, 100)).toBe(0);
    });

    it('returns 0 for a zero or non-finite denominator (so the bar never gets a NaN/Infinity width)', () => {
        expect(toPercentage(5, 0)).toBe(0);
        expect(toPercentage(0, 0)).toBe(0);
    });
});
