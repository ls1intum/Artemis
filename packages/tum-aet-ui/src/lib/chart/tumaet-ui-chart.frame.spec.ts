import { AXIS_TITLE_FONT_SIZE, TICK_GAP, axisTitleViews, cartesianFrame, seriesLegendItems } from './tumaet-ui-chart.frame';

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

describe('cartesian chart labels', () => {
    it('omits unnamed axes while retaining x-before-y ordering and plot-relative coordinates', () => {
        const plot = { left: 30, top: 10, width: 200, height: 100 };
        const margin = { left: 40, top: 20, right: 10, bottom: 30 };
        const horizontalTitle = { text: 'Category', x: 100, y: 128, rotate: 0 };
        const verticalTitle = { text: 'Count', x: -(40 - AXIS_TITLE_FONT_SIZE), y: 50, rotate: -90 };
        expect(axisTitleViews(plot, margin, 'Category', 'Count')).toEqual([horizontalTitle, verticalTitle]);
        expect(axisTitleViews(plot, margin, '', 'Count')).toEqual([verticalTitle]);
        expect(axisTitleViews(plot, margin, 'Category')).toEqual([horizontalTitle]);
        expect(axisTitleViews(plot, margin)).toEqual([]);
    });

    it('keeps legend keys stable when unnamed and reference series are omitted', () => {
        const series = [{ data: [1] }, { label: 'Measured', data: [2], color: 'var(--series)' }, { label: 'Reference', data: [3], referenceLine: true }, { label: '', data: [4] }];
        const measured = { key: '1', label: 'Measured', color: 'var(--series)', hidden: true };
        const reference = { key: '2', label: 'Reference', color: 'var(--tumaet-ui-primary-color)', hidden: false };
        const hidden = new Set(['1']);
        expect(seriesLegendItems(series, hidden)).toEqual([measured, reference]);
        expect(seriesLegendItems(series, hidden, 'exclude')).toEqual([measured]);
        expect(hidden).toEqual(new Set(['1']));
    });
});
