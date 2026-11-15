/** A point in plot coordinates. */
export interface CurvePoint {
    x: number;
    y: number;
}

function moveAndLine(points: readonly CurvePoint[]): string {
    return points.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${point.y}`).join('');
}

/**
 * The weighted harmonic mean of the slopes on either side of a point, which is the tangent that
 * keeps a cubic segment monotone (Fritsch-Carlson). A sign change means the point is a local
 * extremum, where a zero tangent is the only way to avoid overshooting it.
 */
function tangent(previous: CurvePoint, point: CurvePoint, next: CurvePoint): number {
    const leftRun = point.x - previous.x;
    const rightRun = next.x - point.x;
    const leftSlope = (point.y - previous.y) / (leftRun || 1);
    const rightSlope = (next.y - point.y) / (rightRun || 1);
    if (leftSlope * rightSlope <= 0) {
        return 0;
    }
    const weighted = (2 * rightRun + leftRun) / (3 * (leftRun + rightRun)) / leftSlope + (rightRun + 2 * leftRun) / (3 * (leftRun + rightRun)) / rightSlope;
    return 1 / weighted;
}

/**
 * Builds an SVG path through the points using monotone cubic interpolation, the equivalent of d3's
 * `curveMonotoneX`. Unlike a plain cubic spline it never overshoots a data point, so a series that
 * only rises is never drawn dipping below a value it actually reached.
 */
export function monotoneCubicPath(points: readonly CurvePoint[]): string {
    if (points.length < 3) {
        return moveAndLine(points);
    }
    const tangents = points.map((point, index) => {
        if (index === 0) {
            return (points[1].y - point.y) / (points[1].x - point.x || 1);
        }
        if (index === points.length - 1) {
            return (point.y - points[index - 1].y) / (point.x - points[index - 1].x || 1);
        }
        return tangent(points[index - 1], point, points[index + 1]);
    });

    let path = `M${points[0].x},${points[0].y}`;
    for (let i = 0; i < points.length - 1; i++) {
        const from = points[i];
        const to = points[i + 1];
        const third = (to.x - from.x) / 3;
        path += `C${from.x + third},${from.y + tangents[i] * third},${to.x - third},${to.y - tangents[i + 1] * third},${to.x},${to.y}`;
    }
    return path;
}

/** Builds a straight-segment SVG path through the points. */
export function linearPath(points: readonly CurvePoint[]): string {
    return moveAndLine(points);
}

/**
 * Splits a series into the runs of consecutive defined points, so that gaps stay gaps. With
 * `spanGaps` the points are treated as one run and the line bridges the missing values.
 */
export function segmentsOf(points: readonly (CurvePoint | undefined)[], spanGaps: boolean): CurvePoint[][] {
    const defined = points.filter((point): point is CurvePoint => point !== undefined);
    if (spanGaps) {
        return defined.length ? [defined] : [];
    }
    const segments: CurvePoint[][] = [];
    let current: CurvePoint[] = [];
    for (const point of points) {
        if (point) {
            current.push(point);
        } else if (current.length) {
            segments.push(current);
            current = [];
        }
    }
    if (current.length) {
        segments.push(current);
    }
    return segments;
}
