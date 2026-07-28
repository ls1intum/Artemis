import { NgTemplateOutlet } from '@angular/common';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { ChangeDetectionStrategy, Component, TemplateRef, TrackByFunction, booleanAttribute, computed, input } from '@angular/core';
import { TumUiTableSize } from './tum-ui-table.directive';

const HEADER_PADDING: Record<TumUiTableSize, string> = {
    small: 'px-2 py-1.5',
    normal: 'px-4 py-3',
    large: 'px-5 py-4',
};

/**
 * Virtualized companion to {@link TumUiTableDirective}.
 *
 * PrimeNG-free drop-in for a `p-table` running in `[virtualScroll]` mode (as the admin logs page does for
 * its large logger list): it renders a sticky, token-styled header plus a CDK `cdk-virtual-scroll-viewport`
 * body that only materializes the visible fixed-height rows.
 *
 * TRADEOFF (documented, per the kit contract): CDK's virtual scroller transforms a spacer element and is
 * incompatible with native `<table>` row layout, so the body is a `role="table"` **div grid**, not a real
 * `<table>`. It is styled with the *same* semantic surface tokens, paddings, and 1px separators as
 * {@link TumUiTableDirective}, so it renders pixel-identically; only the underlying element is a div. Use
 * this only where virtualization is required (thousands of rows); reach for `[tumUiTable]` otherwise.
 *
 * The consumer projects the header row cells as light content and supplies a `rowTemplate` for the body
 * row cells (both should lay their cells out with flex so the header and rows align on the same widths).
 * Sorting is intentionally NOT built in: the logs page sorts via Artemis' own `jhiSort`/`jhiSortBy`, which
 * the consumer keeps in the projected header.
 */
@Component({
    selector: 'tum-ui-table-virtual-scroll',
    templateUrl: './tum-ui-table-virtual-scroll.component.html',
    styleUrl: './tum-ui-table-virtual-scroll.component.scss',
    imports: [ScrollingModule, NgTemplateOutlet],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTableVirtualScrollComponent<T> {
    /** The full row list; only the visible slice is rendered (parity with p-table `[value]` + `[virtualScroll]`). */
    readonly items = input.required<readonly T[]>();
    /** Fixed row height in px (parity with p-table `[virtualScrollItemSize]`); MUST match the rendered row height. */
    readonly itemSize = input.required<number>();
    /** Body-row template; receives `{ $implicit: row, index }` (drop-in for p-table's `#body` template). */
    readonly rowTemplate = input.required<TemplateRef<{ $implicit: T; index: number }>>();

    /** Cell density, matched to {@link TumUiTableDirective}. */
    readonly size = input<TumUiTableSize>('normal');
    /** Zebra-stripe the rows (parity with p-table `[stripedRows]`). */
    readonly striped = input(false, { transform: booleanAttribute });
    /** Tint rows on hover (parity with p-table `[rowHover]`). */
    readonly rowHover = input(false, { transform: booleanAttribute });
    /**
     * Viewport height: `'flex'` (default) fills the bounded flex parent — the host is a `min-h-0` flex column,
     * so wrap it in a bounded flex container exactly like the logs page (`scrollHeight="flex"`). A CSS length
     * (e.g. `'24rem'`) caps the viewport at that height instead.
     */
    readonly scrollHeight = input<string>('flex');
    /** Minimum table width before horizontal scroll kicks in (drop-in for p-table `[tableStyle]="{'min-width'}"`). */
    readonly minWidth = input<string | undefined>(undefined);
    /** Row identity for CDK diffing; defaults to identity tracking. */
    readonly trackBy = input<TrackByFunction<T> | undefined>(undefined);
    readonly ariaDescribedBy = input<string | undefined>(undefined);

    protected readonly isFlexHeight = computed(() => this.scrollHeight() === 'flex');
    protected readonly maxHeight = computed(() => (this.isFlexHeight() ? undefined : this.scrollHeight()));
    protected readonly effectiveTrackBy = computed<TrackByFunction<T>>(() => this.trackBy() ?? ((_, item) => item));

    protected readonly headerClasses = computed(() => {
        const base =
            'tum-ui-vs-header flex text-sm font-semibold text-tum-ui-surface-700 bg-tum-ui-surface-0 dark:text-tum-ui-surface-0 dark:bg-tum-ui-surface-900 ' +
            'border-b border-solid border-tum-ui-surface-200 dark:border-tum-ui-surface-800';
        return `${base} ${HEADER_PADDING[this.size()]}`;
    });

    protected readonly rowClasses = computed(() => {
        const base =
            'tum-ui-vs-row flex items-center text-sm text-tum-ui-surface-900 dark:text-tum-ui-surface-0 border-b border-solid border-tum-ui-surface-200 dark:border-tum-ui-surface-800';
        const hover = this.rowHover() ? ' hover:bg-tum-ui-surface-100 dark:hover:bg-tum-ui-surface-800' : '';
        return `${base}${hover} ${HEADER_PADDING[this.size()]}`;
    });

    /**
     * Stripe class for a row, keyed on the DATA index — not a CSS `:nth-child(odd)` variant. Under `cdkVirtualFor`
     * only the visible window is in the DOM, so `:nth-child` parity shifts as you scroll and a row's stripe would
     * flicker; the data index is stable. Stripes 0-based-even rows to match the non-virtual `[tumUiTable]` (whose
     * `tbody tr:nth-child(odd)` = 1-based-odd = 0-based-even).
     */
    protected stripeClass(index: number): string {
        return this.striped() && index % 2 === 0 ? ' bg-tum-ui-surface-50 dark:bg-tum-ui-surface-950' : '';
    }
}
