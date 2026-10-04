import { TICK_GAP, cartesianFrame } from './tumaet-ui-chart.frame';

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
        });
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

    it('reserves the horizontal chart label budget inside the left margin', () => {
        const frame = frameFor([longTitle, 'Short'], true);

        expect(frame.categoryLabelBudget).toBe(frame.margin.left - TICK_GAP);
    });
});
