import { describe, expect, it, vi } from 'vitest';
import { booleanLabel, stableStringify, valuesDiffer } from 'app/exercise/version-history/shared/version-history.utils';

describe('booleanLabel', () => {
    const translateService = {
        instant: vi.fn((key: string) => {
            if (key === 'artemisApp.exercise.yes') return 'Yes';
            if (key === 'artemisApp.exercise.no') return 'No';
            return key;
        }),
    };

    it('should return translated "Yes" for true', () => {
        expect(booleanLabel(translateService as any, true)).toBe('Yes');
        expect(translateService.instant).toHaveBeenCalledWith('artemisApp.exercise.yes');
    });

    it('should return translated "No" for false', () => {
        expect(booleanLabel(translateService as any, false)).toBe('No');
        expect(translateService.instant).toHaveBeenCalledWith('artemisApp.exercise.no');
    });

    it('should return undefined for undefined', () => {
        expect(booleanLabel(translateService as any, undefined)).toBeUndefined();
    });
});

describe('stableStringify', () => {
    it('should return undefined for undefined input', () => {
        expect(stableStringify(undefined)).toBeUndefined();
    });

    it('should return stable string for primitives', () => {
        expect(stableStringify('hello')).toBe('"hello"');
        expect(stableStringify(42)).toBe('42');
        expect(stableStringify(true)).toBe('true');
    });

    it('should sort object keys alphabetically', () => {
        expect(stableStringify({ b: 1, a: 2 })).toBe(stableStringify({ a: 2, b: 1 }));
        expect(stableStringify({ b: 1, a: 2 })).toBe('{"a":2,"b":1}');
    });

    it('should sort arrays for stable comparison', () => {
        expect(stableStringify([3, 1, 2])).toBe(stableStringify([1, 2, 3]));
    });

    it('should handle nested objects with stable key ordering', () => {
        const obj1 = { z: { b: 1, a: 2 }, y: 3 };
        const obj2 = { y: 3, z: { a: 2, b: 1 } };
        expect(stableStringify(obj1)).toBe(stableStringify(obj2));
    });
});

describe('valuesDiffer', () => {
    it('should return false for identical primitives', () => {
        expect(valuesDiffer(42, 42)).toBe(false);
        expect(valuesDiffer('hello', 'hello')).toBe(false);
        expect(valuesDiffer(true, true)).toBe(false);
    });

    it('should return true for different primitives', () => {
        expect(valuesDiffer(1, 2)).toBe(true);
        expect(valuesDiffer('a', 'b')).toBe(true);
        expect(valuesDiffer(true, false)).toBe(true);
    });

    it('should return false for undefined vs undefined', () => {
        expect(valuesDiffer(undefined, undefined)).toBe(false);
    });

    it('should return true for undefined vs a value', () => {
        expect(valuesDiffer(undefined, 42)).toBe(true);
        expect(valuesDiffer('hello', undefined)).toBe(true);
    });

    it('should return false for objects with same keys in different order', () => {
        expect(valuesDiffer({ a: 1, b: 2 }, { b: 2, a: 1 })).toBe(false);
    });

    it('should return true for objects with different values', () => {
        expect(valuesDiffer({ a: 1 }, { a: 2 })).toBe(true);
    });

    it('should return false for arrays with same elements in different order', () => {
        expect(valuesDiffer([1, 2, 3], [3, 1, 2])).toBe(false);
    });

    it('should return true for arrays with different elements', () => {
        expect(valuesDiffer([1, 2, 3], [4, 5, 6])).toBe(true);
    });

    it('should return false for deeply nested identical objects', () => {
        const obj1 = { a: { b: { c: [3, 2, 1] } }, d: 'hello' };
        const obj2 = { d: 'hello', a: { b: { c: [1, 2, 3] } } };
        expect(valuesDiffer(obj1, obj2)).toBe(false);
    });
});
