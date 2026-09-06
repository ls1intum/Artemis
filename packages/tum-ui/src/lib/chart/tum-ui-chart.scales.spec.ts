import { allIntegers, bandScale, finiteValues, linearScale, niceDomain, tickStep } from './tum-ui-chart.scales';

describe('chart scales', () => {
    describe('bandScale', () => {
        it('should spread the categories evenly across the range', () => {
            const scale = bandScale(3, 300, 0);
            expect(scale.bandwidth).toBe(100);
            expect(scale.position(0)).toBe(0);
            expect(scale.center(1)).toBe(150);
        });

        /**
         * Two exercises may share a title, and an untitled one contributes an empty label. Addressing
         * bands by label would put them on the same band and hide one bar behind the other.
         */
        it('should give every category its own band even when labels repeat', () => {
            const scale = bandScale(3, 300, 0);
            expect(scale.center(0)).not.toBe(scale.center(1));
            expect(scale.center(1)).not.toBe(scale.center(2));
        });

        it('should not divide by zero when there is no category', () => {
            expect(bandScale(0, 300).bandwidth).toBeGreaterThanOrEqual(0);
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

    describe('non-finite data', () => {
        /**
         * A single missing figure in a server response used to poison the whole domain, so every bar
         * was drawn at NaN and the chart came out blank instead of losing one bar.
         */
        it('should drop values a scale cannot place', () => {
            expect(finiteValues([1, undefined, Number.NaN, null, Number.POSITIVE_INFINITY, 4])).toEqual([1, 4]);
        });

        it('should fall back to a unit domain when the extent is not finite', () => {
            expect(niceDomain(Number.NaN, 10)).toEqual([0, 1]);
            expect(niceDomain(0, Number.POSITIVE_INFINITY)).toEqual([0, 1]);
        });

        /** An infinite bound would otherwise loop forever and exhaust memory. */
        it('should produce no ticks for a non-finite domain', () => {
            expect(linearScale([0, Number.POSITIVE_INFINITY], [0, 1]).ticks(5)).toEqual([]);
            expect(linearScale([Number.NaN, Number.NaN], [0, 1]).ticks(5)).toEqual([]);
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
