/**
 * Vitest tests for filterNaN utility.
 */
import { describe, expect, it } from 'vitest';
import { filterNaN } from 'app/core/admin/metrics/filterNaN-util';

describe('filterNaN', () => {
    it('should return 0 for NaN', () => {
        expect(filterNaN(NaN)).toBe(0);
    });

    it('should return number for a number', () => {
        expect(filterNaN(12345)).toBe(12345);
    });
});
