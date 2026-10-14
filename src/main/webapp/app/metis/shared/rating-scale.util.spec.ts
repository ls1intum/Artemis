import { describe, expect, it } from 'vitest';
import { formatCountdown, ratingSeverity } from 'app/metis/shared/rating-scale.util';

describe('rating-scale.util', () => {
    describe('ratingSeverity', () => {
        it.each([
            [1, 'lowest'],
            [2, 'low'],
            [3, 'neutral'],
            [4, 'high'],
            [5, 'highest'],
        ])('should map %i of 5 to %s', (score, severity) => {
            expect(ratingSeverity(score, 5)).toBe(severity);
        });

        it.each([
            [1, 'lowest'],
            [2, 'lowest'],
            [3, 'low'],
            [4, 'neutral'],
            [5, 'high'],
            [6, 'highest'],
            [7, 'highest'],
        ])('should map %i of 7 to %s', (score, severity) => {
            expect(ratingSeverity(score, 7)).toBe(severity);
        });

        it('should clamp ratings outside the scale onto its ends and round fractions', () => {
            expect(ratingSeverity(0, 5)).toBe('lowest');
            expect(ratingSeverity(9, 5)).toBe('highest');
            expect(ratingSeverity(2.6, 5)).toBe('neutral');
        });

        it('should keep the ends of scales without five steps', () => {
            expect([1, 2, 3, 4].map((score) => ratingSeverity(score, 4))).toEqual(['lowest', 'low', 'high', 'highest']);
            expect([1, 2, 3].map((score) => ratingSeverity(score, 3))).toEqual(['lowest', 'neutral', 'highest']);
            expect([1, 2].map((score) => ratingSeverity(score, 2))).toEqual(['lowest', 'highest']);
        });

        it('should fall back to neutral for a scale without steps or a rating that is not a number', () => {
            expect(ratingSeverity(1, 1)).toBe('neutral');
            expect(ratingSeverity(Number.NaN, 5)).toBe('neutral');
        });
    });

    describe('formatCountdown', () => {
        it.each([
            [240, '4:00'],
            [150, '2:30'],
            [30, '0:30'],
            [0, '0:00'],
            [59.9, '0:59'],
            [3661, '61:01'],
            [-5, '0:00'],
            [Number.NaN, '0:00'],
        ])('should format %s seconds as %s', (seconds, display) => {
            expect(formatCountdown(seconds)).toBe(display);
        });
    });
});
