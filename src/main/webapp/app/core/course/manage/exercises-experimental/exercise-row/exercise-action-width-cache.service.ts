import { Injectable, Signal, signal } from '@angular/core';

/**
 * Shared cache of action-button widths for the experimental exercise table.
 *
 * A button's natural width depends only on its visual signature (icon + label + style), which is identical across
 * rows, and the action column has a single width for the whole table. So each distinct button only needs to be
 * measured once for the entire table rather than once per row: the first row that renders a given button measures it
 * and stores the result here; every other row reads it from the cache and never renders its own measurement DOM.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseActionWidthCacheService {
    private readonly widths = signal<ReadonlyMap<string, number>>(new Map());

    /** Reactive view of the cached widths; read it to make a computed depend on cache updates. */
    readonly widthsBySignature: Signal<ReadonlyMap<string, number>> = this.widths.asReadonly();

    has(signature: string): boolean {
        return this.widths().has(signature);
    }

    get(signature: string): number | undefined {
        return this.widths().get(signature);
    }

    /** Adds any not-yet-known signatures. No-op (and no signal emission) if every signature was already cached. */
    contribute(measured: ReadonlyMap<string, number>): void {
        const current = this.widths();
        let next: Map<string, number> | undefined;
        for (const [signature, width] of measured) {
            if (!current.has(signature)) {
                next ??= new Map(current);
                next.set(signature, width);
            }
        }
        if (next) {
            this.widths.set(next);
        }
    }
}
