/** A slice of a doughnut or pie chart, in radians measured clockwise from twelve o'clock. */
export interface ArcSlice {
    startAngle: number;
    endAngle: number;
}

const TAU = Math.PI * 2;
/** Guards against the degenerate case where a full circle's start and end point coincide. */
const FULL_CIRCLE_EPSILON = 1e-6;

function pointOnCircle(centerX: number, centerY: number, radius: number, angle: number): [number, number] {
    return [centerX + radius * Math.sin(angle), centerY - radius * Math.cos(angle)];
}

/**
 * Builds the SVG path of a single ring segment. An `innerRadius` of 0 produces a pie slice.
 *
 * A slice covering the whole circle is drawn as two half arcs, because a single arc whose start and
 * end coincide renders as nothing at all.
 */
export function arcPath(centerX: number, centerY: number, innerRadius: number, outerRadius: number, startAngle: number, endAngle: number): string {
    const sweep = endAngle - startAngle;
    if (sweep <= 0) {
        return '';
    }
    if (sweep >= TAU - FULL_CIRCLE_EPSILON) {
        const half = startAngle + Math.PI;
        return arcPath(centerX, centerY, innerRadius, outerRadius, startAngle, half) + arcPath(centerX, centerY, innerRadius, outerRadius, half, startAngle + TAU);
    }
    const largeArc = sweep > Math.PI ? 1 : 0;
    const [outerStartX, outerStartY] = pointOnCircle(centerX, centerY, outerRadius, startAngle);
    const [outerEndX, outerEndY] = pointOnCircle(centerX, centerY, outerRadius, endAngle);

    if (innerRadius <= 0) {
        return `M${centerX},${centerY}L${outerStartX},${outerStartY}A${outerRadius},${outerRadius} 0 ${largeArc} 1 ${outerEndX},${outerEndY}Z`;
    }
    const [innerEndX, innerEndY] = pointOnCircle(centerX, centerY, innerRadius, endAngle);
    const [innerStartX, innerStartY] = pointOnCircle(centerX, centerY, innerRadius, startAngle);
    return (
        `M${outerStartX},${outerStartY}` +
        `A${outerRadius},${outerRadius} 0 ${largeArc} 1 ${outerEndX},${outerEndY}` +
        `L${innerEndX},${innerEndY}` +
        `A${innerRadius},${innerRadius} 0 ${largeArc} 0 ${innerStartX},${innerStartY}Z`
    );
}

/** Splits values into consecutive slices of a full circle. A total of 0 produces no slices. */
export function sliceAngles(values: readonly number[]): ArcSlice[] {
    const contribution = (value: number) => (Number.isFinite(value) ? Math.max(value, 0) : 0);
    const total = values.reduce((sum, value) => sum + contribution(value), 0);
    if (total <= 0) {
        return values.map(() => ({ startAngle: 0, endAngle: 0 }));
    }
    let angle = 0;
    return values.map((value) => {
        const startAngle = angle;
        angle += (contribution(value) / total) * TAU;
        return { startAngle, endAngle: angle };
    });
}
