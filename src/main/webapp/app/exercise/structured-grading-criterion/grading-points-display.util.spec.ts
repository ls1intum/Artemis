import { describe, expect, it } from 'vitest';
import { pointsLabel, pointsSeverity } from 'app/exercise/structured-grading-criterion/grading-points-display.util';

describe('grading points display helpers', () => {
    it('should pick the tag severity from the sign of the points', () => {
        expect(pointsSeverity(4)).toBe('success');
        expect(pointsSeverity(-2)).toBe('danger');
        expect(pointsSeverity(0)).toBe('secondary');
        expect(pointsSeverity(undefined)).toBe('secondary');
    });

    it('should format the points with a leading sign only for positive values', () => {
        expect(pointsLabel(10)).toBe('+10');
        expect(pointsLabel(-5)).toBe('-5');
        expect(pointsLabel(0)).toBe('0');
        expect(pointsLabel(undefined)).toBe('0');
    });
});
