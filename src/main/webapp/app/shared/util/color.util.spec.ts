import { getBackgroundColorHue, getColorBrightness, getContrastingTextColor, isColorDark } from 'app/shared/util/color.utils';
import { deterministicRandomValueFromString } from 'app/shared/util/text.utils';

jest.mock('app/shared/util/text.utils', () => ({
    deterministicRandomValueFromString: jest.fn(),
}));

describe('color utils', () => {
    describe('getBackgroundColorHue', () => {
        const mockDeterministic = deterministicRandomValueFromString as jest.Mock;

        beforeEach(() => {
            jest.clearAllMocks();
        });

        it('returns correct HSL using deterministicRandomValueFromString', () => {
            mockDeterministic.mockReturnValue(0.5);
            const seed = 'seed-string';
            const result = getBackgroundColorHue(seed);
            expect(mockDeterministic).toHaveBeenCalledWith(seed);
            expect(result).toBe('hsl(180, 50%, 50%)');
        });

        it('uses Math.random when seed undefined', () => {
            mockDeterministic.mockReturnValue(0.25);
            const randomValue = 0.123456;
            const mathRandomSpy = jest.spyOn(Math, 'random').mockReturnValue(randomValue);

            const result = getBackgroundColorHue(undefined);
            expect(mathRandomSpy).toHaveBeenCalled();
            expect(mockDeterministic).toHaveBeenCalledWith(randomValue.toString());
            expect(result).toBe(`hsl(${0.25 * 360}, 50%, 50%)`);

            mathRandomSpy.mockRestore();
        });
    });

    describe('getColorBrightness', () => {
        it('calculates brightness for black (#000000)', () => {
            expect(getColorBrightness('#000000')).toBe(0);
        });

        it('calculates brightness for white without hash', () => {
            expect(getColorBrightness('FFFFFF')).toBe(255);
        });

        it('is case-insensitive and handles mixed-case', () => {
            const expected = (170 * 299 + 187 * 587 + 204 * 114) / 1000;
            expect(getColorBrightness('#AaBbCc')).toBe(expected);
        });
    });

    describe('isColorDark', () => {
        it('returns true for dark colors', () => {
            expect(isColorDark('#000000')).toBeTrue();
        });

        it('returns false for light colors', () => {
            expect(isColorDark('#FFFFFF')).toBeFalse();
        });

        it('threshold at 128 returns false', () => {
            expect(isColorDark('#808080')).toBeFalse();
        });
    });

    describe('getContrastingTextColor', () => {
        it('returns white on dark backgrounds', () => {
            expect(getContrastingTextColor('#000000')).toBe('white');
        });

        it('returns black on light backgrounds', () => {
            expect(getContrastingTextColor('#FFFFFF')).toBe('black');
        });
    });

    describe('edge cases for invalid color formats', () => {
        it('getColorBrightness returns NaN for invalid hex', () => {
            expect(isNaN(getColorBrightness('GGGGGG'))).toBeTrue();
        });

        it('isColorDark returns false for NaN brightness', () => {
            expect(isColorDark('GGGGGG')).toBeFalse();
        });

        it('getContrastingTextColor returns black for NaN brightness', () => {
            expect(getContrastingTextColor('GGGGGG')).toBe('black');
        });
    });
});
