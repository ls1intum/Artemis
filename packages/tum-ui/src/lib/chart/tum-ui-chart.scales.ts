/**
 * Minimal scale and tick helpers for the chart components.
 *
 * These cover the linear and band scales the charts need. They deliberately do not depend on
 * `d3-scale` / `d3-array`: the required surface is small enough that pulling in six transitive
 * packages is not justified. If log, time or diverging scales are ever needed, swapping in
 * `d3-scale` behind these signatures is a contained change.
 */

/**
 * Maps a run of categories to evenly spaced, equally wide bands.
 *
 * Bands are addressed by position, not by label: two categories may legitimately carry the same
 * label — two exercises with the same title, or two untitled ones — and keying on the label would
 * collapse them onto one band and hide a bar behind another.
 */
export interface BandScale {
    /** Start coordinate of the band at `index`. */
    position(index: number): number;
    readonly bandwidth: number;
    /** Center coordinate of the band at `index`. */
    center(index: number): number;
}

/** Maps a numeric domain onto a pixel range. */
export interface LinearScale {
    (value: number): number;
    readonly domain: readonly [number, number];
    /**
     * @param count approximate number of ticks
     * @param minStep smallest allowed increment; pass 1 for an axis that only ever shows whole
     *        numbers, so that a small range such as 0–3 does not produce half steps
     */
    ticks(count?: number, minStep?: number): number[];
}

export function bandScale(count: number, size: number, padding = 0.25): BandScale {
    const step = size / Math.max(count, 1);
    const bandwidth = Math.max(step * (1 - padding), 0);
    const offset = (step - bandwidth) / 2;
    const position = (index: number) => index * step + offset;
    return { position, bandwidth, center: (index: number) => position(index) + bandwidth / 2 };
}

const E10 = Math.sqrt(50);
const E5 = Math.sqrt(10);
const E2 = Math.sqrt(2);

/**
 * The d3-array tick step: the "nicest" round increment of 1, 2 or 5 times a power of ten.
 *
 * `minStep` raises the result to the next multiple of itself. Counts of things are integers, and an
 * axis that labels them with an integer formatter would otherwise render "0, 1, 1, 2, 2, 3" once a
 * half step is collapsed by the formatter.
 */
export function tickStep(start: number, stop: number, count: number, minStep = 0): number {
    const rough = Math.abs(stop - start) / Math.max(count, 1);
    if (!Number.isFinite(rough) || rough === 0) {
        return Math.max(1, minStep);
    }
    const power = Math.pow(10, Math.floor(Math.log10(rough)));
    const error = rough / power;
    let step = power;
    if (error >= E10) {
        step = power * 10;
    } else if (error >= E5) {
        step = power * 5;
    } else if (error >= E2) {
        step = power * 2;
    }
    return minStep > 0 ? Math.max(minStep, Math.ceil(step / minStep) * minStep) : step;
}

/**
 * Rounds a value to the precision implied by `step`, so that accumulated floating point error does
 * not surface as tick labels like `0.30000000000000004`.
 */
function roundToStep(value: number, step: number): number {
    const decimals = Math.max(0, -Math.floor(Math.log10(step)) + 1);
    return Number(value.toFixed(Math.min(decimals, 20)));
}

/** Extends a domain outwards to the next round tick, matching d3's `scale.nice()`. */
export function niceDomain(min: number, max: number, count = 5, minStep = 0): [number, number] {
    if (!Number.isFinite(min) || !Number.isFinite(max)) {
        return [0, 1];
    }
    if (min === max) {
        return min === 0 ? [0, 1] : [Math.min(0, min), Math.max(0, max)];
    }
    const step = tickStep(min, max, count, minStep);
    return [roundToStep(Math.floor(min / step) * step, step), roundToStep(Math.ceil(max / step) * step, step)];
}

export function linearScale(domain: readonly [number, number], range: readonly [number, number]): LinearScale {
    const [d0, d1] = domain;
    const [r0, r1] = range;
    const span = d1 - d0;
    const scale = ((value: number) => (span === 0 ? r0 : r0 + ((value - d0) / span) * (r1 - r0))) as {
        (value: number): number;
        domain: LinearScale['domain'];
        ticks: LinearScale['ticks'];
    };
    scale.domain = domain;
    scale.ticks = (count = 5, minStep = 0) => {
        if (span === 0) {
            return Number.isFinite(d0) ? [d0] : [];
        }
        const step = tickStep(d0, d1, count, minStep);
        const first = Math.ceil(d0 / step);
        const last = Math.floor(d1 / step);
        // An infinite bound would make the loop below run forever and exhaust memory.
        if (!Number.isFinite(first) || !Number.isFinite(last)) {
            return [];
        }
        const result: number[] = [];
        for (let i = first; i <= last; i++) {
            result.push(roundToStep(i * step, step));
        }
        return result;
    };
    return scale;
}

/**
 * Approximates the rendered width of a label in px.
 *
 * Charts need tick label widths to reserve axis margins before the SVG is laid out, and measuring
 * every label in the DOM on each resize is disproportionate. The factor matches the average glyph
 * advance of the UI font at the chart's tick font size and errs on the generous side, since an
 * over-wide margin only costs plot area whereas an under-wide one clips the label.
 */
export function approximateTextWidth(text: string, fontSize: number): number {
    return text.length * fontSize * 0.58;
}

/** True when every value is a whole number, meaning the axis should not show fractional ticks. */
export function allIntegers(values: readonly (number | undefined)[]): boolean {
    return values.every((value) => value === undefined || Number.isInteger(value));
}

/**
 * Keeps only the values a scale can actually place. A single NaN — one missing field in a server
 * response — would otherwise poison the domain and blank the whole chart rather than one bar.
 */
export function finiteValues(values: readonly (number | undefined | null)[]): number[] {
    return values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
}
