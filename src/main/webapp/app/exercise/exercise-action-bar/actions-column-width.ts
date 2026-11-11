import { Signal, computed, signal } from '@angular/core';

/**
 * Sizes a table's actions column to the widest current row's `jhi-exercise-action-bar` buttons.
 *
 * Rows report the width they need by id; only the rows the table currently shows count, so the column narrows again when
 * the widest row is deleted or moves to another table. The width is a percentage of the visible table width, not a pixel
 * value: in automatic table layout a pixel width on a cell is a minimum, which would make the table scroll instead of
 * letting the column shrink and fold buttons into the menu.
 */
export class ActionsColumnWidth {
    private readonly widths = signal<ReadonlyMap<number, number>>(new Map());
    private readonly visibleWidth = signal(0);

    /** CSS width for the actions cells, or undefined until a row has measured its buttons and the table its width. */
    readonly cssWidth = computed<string | undefined>(() => {
        const widths = this.widths();
        const widest = Math.max(0, ...this.rowIds().map((id) => widths.get(id) ?? 0));
        const visible = this.visibleWidth();
        if (widest === 0 || visible === 0) {
            return undefined;
        }
        // Rounded up with a pixel to spare: a column a fraction of a pixel too narrow would fold a button.
        return `${(Math.ceil(((widest + 1) / visible) * 100_000) / 1000).toFixed(3)}%`;
    });

    constructor(private readonly rowIds: Signal<readonly number[]>) {}

    /** Records the width (px) the row with this id needs to show its actions. */
    report(rowId: number, width: number): void {
        if (this.widths().get(rowId) !== width) {
            this.widths.update((widths) => new Map(widths).set(rowId, width));
        }
    }

    /** Tracks the width of the element the table fills; returns the function that stops tracking. */
    observe(element: HTMLElement): () => void {
        const observer = new ResizeObserver(() => this.visibleWidth.set(element.clientWidth));
        observer.observe(element);
        this.visibleWidth.set(element.clientWidth);
        return () => observer.disconnect();
    }
}
