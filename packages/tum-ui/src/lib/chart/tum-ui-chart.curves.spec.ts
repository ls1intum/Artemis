import { CurvePoint, linearPath, monotoneCubicPath, segmentsOf } from './tum-ui-chart.curves';

describe('chart curves', () => {
    const rising: CurvePoint[] = [
        { x: 0, y: 100 },
        { x: 10, y: 80 },
        { x: 20, y: 40 },
        { x: 30, y: 10 },
    ];

    describe('linearPath', () => {
        it('should join the points with straight segments', () => {
            expect(linearPath(rising)).toBe('M0,100L10,80L20,40L30,10');
        });

        it('should return an empty path for no points', () => {
            expect(linearPath([])).toBe('');
        });
    });

    describe('monotoneCubicPath', () => {
        it('should fall back to straight segments for fewer than three points', () => {
            expect(monotoneCubicPath(rising.slice(0, 2))).toBe('M0,100L10,80');
        });

        it('should emit one cubic segment per interval', () => {
            expect(monotoneCubicPath(rising).match(/C/g)).toHaveLength(3);
        });

        /**
         * The property that distinguishes monotone interpolation from a plain cubic spline: a
         * monotone series must never be drawn leaving the range spanned by its own points.
         */
        it('should not overshoot a monotone series', () => {
            const path = monotoneCubicPath(rising);
            const numbers = path.match(/-?\d+(\.\d+)?/g)!.map(Number);
            const ys = numbers.filter((_, index) => index % 2 === 1);
            expect(Math.min(...ys)).toBeGreaterThanOrEqual(10);
            expect(Math.max(...ys)).toBeLessThanOrEqual(100);
        });

        it('should flatten the tangent at a local extremum so the peak is not exceeded', () => {
            const peak: CurvePoint[] = [
                { x: 0, y: 50 },
                { x: 10, y: 10 },
                { x: 20, y: 50 },
            ];
            const numbers = monotoneCubicPath(peak)
                .match(/-?\d+(\.\d+)?/g)!
                .map(Number);
            const ys = numbers.filter((_, index) => index % 2 === 1);
            expect(Math.min(...ys)).toBeGreaterThanOrEqual(10);
        });
    });

    describe('segmentsOf', () => {
        const withGap = [{ x: 0, y: 1 }, undefined, { x: 2, y: 3 }, { x: 3, y: 4 }];

        it('should split into runs of consecutive points', () => {
            expect(segmentsOf(withGap, false)).toEqual([
                [{ x: 0, y: 1 }],
                [
                    { x: 2, y: 3 },
                    { x: 3, y: 4 },
                ],
            ]);
        });

        it('should join the runs when spanning gaps', () => {
            expect(segmentsOf(withGap, true)).toHaveLength(1);
        });

        it('should return nothing when every point is missing', () => {
            expect(segmentsOf([undefined, undefined], true)).toEqual([]);
        });
    });
});
