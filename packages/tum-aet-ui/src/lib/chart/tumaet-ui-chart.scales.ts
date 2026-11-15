/** Equal-width bands addressed by index so repeated labels remain distinct categories. */
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

/** Estimates label width in pixels before SVG layout, using an average glyph width of 0.58 em. */
export function approximateTextWidth(text: string, fontSize: number): number {
    return text.length * fontSize * 0.58;
}

/** True when every value is a whole number, meaning the axis should not show fractional ticks. */
export function allIntegers(values: readonly (number | undefined)[]): boolean {
    return values.every((value) => value === undefined || Number.isInteger(value));
}

/** Excludes missing and non-finite values from domain calculations. */
export function finiteValues(values: readonly (number | undefined | null)[]): number[] {
    return values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
}
