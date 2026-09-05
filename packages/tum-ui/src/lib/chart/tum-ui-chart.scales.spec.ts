import { allIntegers, bandScale, linearScale, niceDomain, tickStep } from './tum-ui-chart.scales';

describe('chart scales', () => {
    describe('bandScale', () => {
        it('should spread the categories evenly across the range', () => {
            const scale = bandScale(['a', 'b', 'c'], 300, 0);
            expect(scale.bandwidth).toBe(100);
            expect(scale.position('a')).toBe(0);
            expect(scale.center('b')).toBe(150);
        });

        it('should return undefined for an unknown category', () => {
            expect(bandScale(['a'], 100).position('zzz')).toBeUndefined();
        });
    });

    describe('linearScale', () => {
        it('should map the domain onto the range', () => {
            const scale = linearScale([0, 10], [0, 200]);
            expect(scale(0)).toBe(0);
            expect(scale(5)).toBe(100);
            expect(scale(10)).toBe(200);
        });

        it('should support an inverted range, as used for a value axis', () => {
            const scale = linearScale([0, 10], [200, 0]);
            expect(scale(10)).toBe(0);
        });
    });

    describe('tickStep', () => {
        it('should pick round increments of 1, 2 or 5 times a power of ten', () => {
            expect(tickStep(0, 10, 5)).toBe(2);
            expect(tickStep(0, 100, 5)).toBe(20);
            expect(tickStep(0, 1, 5)).toBe(0.2);
        });

        /**
         * A count of things is a whole number. Without a minimum step, a 0-3 axis steps by 0.5 and an
         * integer tick formatter then renders "0, 1, 1, 2, 2, 3" — the same label twice in a row.
         */
        it('should not step in fractions when the axis only shows whole numbers', () => {
            expect(tickStep(0, 3, 5)).toBe(0.5);
            expect(tickStep(0, 3, 5, 1)).toBe(1);
        });

        it('should round a larger step up to a multiple of the minimum', () => {
            expect(tickStep(0, 12, 5, 1)).toBe(2);
        });
    });

    describe('ticks', () => {
        it('should produce whole-number ticks for a small integer range', () => {
            expect(linearScale([0, 3], [0, 1]).ticks(5, 1)).toEqual([0, 1, 2, 3]);
        });

        it('should still allow fractional ticks when values are not integers', () => {
            expect(linearScale([0, 1], [0, 1]).ticks(5)).toEqual([0, 0.2, 0.4, 0.6, 0.8, 1]);
        });
    });

    describe('niceDomain', () => {
        it('should round the domain outwards to whole numbers when asked', () => {
            // already whole, so the domain is left alone
            expect(niceDomain(0, 3, 5, 1)).toEqual([0, 3]);
            expect(niceDomain(0, 7, 5, 1)).toEqual([0, 7]);
            // a fractional maximum is widened to the next whole tick instead of adding a half step
            expect(niceDomain(0, 7.5, 5, 1)).toEqual([0, 8]);
        });

        it('should widen a degenerate domain', () => {
            expect(niceDomain(0, 0)).toEqual([0, 1]);
        });
    });

    describe('allIntegers', () => {
        it('should ignore gaps and report whether the values are whole numbers', () => {
            expect(allIntegers([1, undefined, 3])).toBe(true);
            expect(allIntegers([1, 2.5])).toBe(false);
            expect(allIntegers([])).toBe(true);
        });
    });
});
