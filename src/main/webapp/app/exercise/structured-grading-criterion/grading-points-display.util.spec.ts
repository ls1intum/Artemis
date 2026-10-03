import { describe, expect, it } from 'vitest';
import { CREDITS_STEP, normalizedCredits, pointsLabel, pointsSeverity, steppedCredits } from 'app/exercise/structured-grading-criterion/grading-points-display.util';

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

    it('should step the points in half-point increments', () => {
        expect(steppedCredits(1, CREDITS_STEP)).toBe(1.5);
        expect(steppedCredits(1, -CREDITS_STEP)).toBe(0.5);
        expect(steppedCredits(undefined, CREDITS_STEP)).toBe(0.5);
    });

    it('should normalize typed points to half-point increments', () => {
        expect(normalizedCredits(0.3)).toBe(0.5);
        expect(normalizedCredits(-0.3)).toBe(-0.5);
        expect(normalizedCredits(1.5)).toBe(1.5);
        expect(normalizedCredits(undefined)).toBeUndefined();
    });

    it('should snap a hand-typed value onto the half-point grid in the direction of travel', () => {
        expect(steppedCredits(1.3, CREDITS_STEP)).toBe(1.5);
        expect(steppedCredits(1.3, -CREDITS_STEP)).toBe(1);
    });
});
