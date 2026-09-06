import { cartesianFrame } from './tum-ui-chart.frame';
import { bandScale, linearScale } from './tum-ui-chart.scales';

/**
 * A vertical chart reserves at most a third of its height for rotated category labels, so a label longer than
 * that has to be shortened. Left unbounded it is drawn in full and runs off the bottom and side of the chart,
 * over whatever follows it — which is what a long exercise title did on the course average-score chart.
 */
describe('cartesianFrame category label budget', () => {
    const longTitle = 'Programming exercise about building a distributed system with fault tolerance and recovery';

    function frameFor(labels: string[], horizontal: boolean) {
        const size = { width: 600, height: 300 };
        return cartesianFrame({
            size,
            labels,
            horizontal,
            valueTicks: [
                { value: 0, text: '0' },
                { value: 100, text: '100' },
            ],
            categoryScale: bandScale(labels.length, horizontal ? size.height : size.width),
            valueScale: linearScale([0, 100], [0, 100]),
        } as never);
    }

    it('gives a vertical chart a finite budget for its category labels', () => {
        const frame = frameFor([longTitle, 'Short'], false);

        expect(Number.isFinite(frame.categoryLabelBudget)).toBe(true);
        expect(frame.categoryLabelBudget).toBeGreaterThan(0);
    });

    it('keeps the budget within the space it actually reserved', () => {
        const frame = frameFor([longTitle, 'Short'], false);

        // The rotated label leans across the bottom margin, so it may not ask for more room than is there.
        const reserved = frame.margin.bottom;
        expect(frame.categoryLabelBudget).toBeLessThanOrEqual(reserved * 2);
    });

    it('still gives a horizontal chart the left margin as its budget', () => {
        const frame = frameFor([longTitle, 'Short'], true);

        expect(Number.isFinite(frame.categoryLabelBudget)).toBe(true);
    });
});
