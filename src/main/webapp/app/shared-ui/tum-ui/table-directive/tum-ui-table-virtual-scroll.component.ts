import { NgTemplateOutlet } from '@angular/common';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { ChangeDetectionStrategy, Component, TemplateRef, TrackByFunction, booleanAttribute, computed, input } from '@angular/core';
import { TumUiTableSize } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';

// Header + body cell paddings as full literal utility strings per size (Tailwind only emits verbatim
// classes). Kept in step with TumUiTableDirective's SIZE_PADDING so the virtualized table and the real
// [tumUiTable] render with identical density.
const HEADER_PADDING: Record<TumUiTableSize, string> = {
    small: 'px-2 py-1.5',
    normal: 'px-4 py-3',
    large: 'px-5 py-4',
};

/**
 * Virtualized companion to {@link TumUiTableDirective}, part of the tum-aet-ui kit.
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
            'tum-ui-vs-header flex text-sm font-semibold text-surface-700 bg-surface-0 dark:text-surface-0 dark:bg-surface-900 ' +
            'border-b border-solid border-surface-200 dark:border-surface-800';
        return `${base} ${HEADER_PADDING[this.size()]}`;
    });

    protected readonly rowClasses = computed(() => {
        const base = 'tum-ui-vs-row flex items-center text-sm text-surface-900 dark:text-surface-0 border-b border-solid border-surface-200 dark:border-surface-800';
        const striped = this.striped() ? ' odd:bg-surface-50 dark:odd:bg-surface-950' : '';
        const hover = this.rowHover() ? ' hover:bg-surface-100 dark:hover:bg-surface-800' : '';
        return `${base}${striped}${hover} ${HEADER_PADDING[this.size()]}`;
    });
}
