import { Directive, booleanAttribute, computed, input, output } from '@angular/core';

/** Cell density, mirroring PrimeNG's p-table `size` (Aura `sm` / default / `lg` paddings). */
export type TumUiTableSize = 'small' | 'normal' | 'large';

/**
 * Sort event emitted by {@link TumUiTableDirective}, shaped to match the `SortEvent` fields
 * (`field` + `order`) that Artemis' admin `(onSort)` handlers already consume: `order` is `1`
 * for ascending and `-1` for descending. Fed straight into the existing handlers unchanged.
 */
export interface TumUiTableSortEvent {
    field: string;
    order: number;
}

const SIZE_PADDING: Record<TumUiTableSize, string> = {
    small: '[&_thead_th]:px-2 [&_thead_th]:py-1.5 [&_tbody_td]:px-2 [&_tbody_td]:py-1.5',
    normal: '[&_thead_th]:px-4 [&_thead_th]:py-3 [&_tbody_td]:px-4 [&_tbody_td]:py-3',
    large: '[&_thead_th]:px-5 [&_thead_th]:py-4 [&_tbody_td]:px-5 [&_tbody_td]:py-4',
};

const HEADER_CLASSES =
    '[&_thead_th]:text-left [&_thead_th]:font-semibold [&_thead_th]:whitespace-nowrap ' +
    '[&_thead_th]:bg-tum-ui-surface-0 [&_thead_th]:text-tum-ui-surface-700 dark:[&_thead_th]:bg-tum-ui-surface-900 dark:[&_thead_th]:text-tum-ui-surface-0 ' +
    '[&_thead_th]:border-b [&_thead_th]:border-solid [&_thead_th]:border-tum-ui-surface-200 dark:[&_thead_th]:border-tum-ui-surface-800';

const BODY_CLASSES =
    '[&_tbody_td]:text-tum-ui-surface-900 dark:[&_tbody_td]:text-tum-ui-surface-0 ' +
    '[&_tbody_td]:border-b [&_tbody_td]:border-solid [&_tbody_td]:border-tum-ui-surface-200 dark:[&_tbody_td]:border-tum-ui-surface-800';

const STRIPED_CLASSES = '[&_tbody_tr:nth-child(odd)]:bg-tum-ui-surface-50 dark:[&_tbody_tr:nth-child(odd)]:bg-tum-ui-surface-950';

const HOVER_CLASSES = '[&_tbody_tr:hover]:bg-tum-ui-surface-100 dark:[&_tbody_tr:hover]:bg-tum-ui-surface-800';

const SCROLLABLE_CLASSES = '[&_thead_th]:sticky [&_thead_th]:top-0 [&_thead_th]:z-10';

/**
 * Low-level styled-table directive.
 *
 * A PrimeNG-free, dependency-free drop-in for using `p-table` as a *styling shell* around hand-written
 * table markup: apply `tumUiTable` to a native `<table>` and it renders pixel-identically to Aura's
 * `datatable` (header cell chrome, body-cell padding, 1px row separators, optional striping / row hover /
 * sticky-header scroll) while the consumer keeps full control of `<thead>/<tbody>/<tr>/<th>/<td>`, their
 * `@for` rows, selection checkboxes, paginators, and multi-element cells. Styling is applied entirely via
 * semantic Tailwind arbitrary-variant utilities on the host, so no encapsulated stylesheet is needed and
 * the classes reach the consumer's descendant cells.
 *
 * Sorting is coordinated with {@link TumUiTableSortableColumnComponent} children and is *controlled*
 * (parity with p-table's `[customSort]` + `[sortField]` + `[sortOrder]`): the directive holds no internal
 * sort state, it reflects the bound `sortField`/`sortOrder` into the header icons and emits
 * {@link TumUiTableSortEvent} on `sortChange` for the consumer to run its own server/manual sort.
 */
@Directive({
    selector: 'table[tumUiTable]',
    host: {
        '[class]': 'hostClasses()',
    },
})
export class TumUiTableDirective {
    /** Cell density (parity with p-table `size`; admin tables use `'small'`). */
    readonly size = input<TumUiTableSize>('normal');
    /** Zebra-stripe the body rows (parity with p-table `[stripedRows]`). */
    readonly striped = input(false, { transform: booleanAttribute });
    /** Pin the header while a bounded ancestor container scrolls (parity with p-table `[scrollable]`). */
    readonly scrollable = input(false, { transform: booleanAttribute });
    /** Tint body rows on hover (parity with p-table `[rowHover]`). */
    readonly rowHover = input(false, { transform: booleanAttribute });
    /** Extra classes forwarded onto the table (drop-in for p-table `styleClass`, e.g. `mt-3`). */
    readonly styleClass = input('');

    /**
     * Active sort field (controlled, parity with p-table `[sortField]`). Drives the sortable-column
     * header icons + `aria-sort`; `undefined` means unsorted.
     */
    readonly sortField = input<string | undefined>(undefined);
    /** Active sort order (controlled, parity with p-table `[sortOrder]`): `1` ascending, `-1` descending. */
    readonly sortOrder = input<number>(1);
    /** Order applied when a *different* (previously unsorted) column is clicked (parity with p-table `defaultSortOrder`). */
    readonly defaultSortOrder = input<number>(1);

    /** Emitted on a sortable-column click; shape matches the admin `(onSort)` handlers' `SortEvent`. */
    readonly sortChange = output<TumUiTableSortEvent>();

    protected readonly hostClasses = computed(() => {
        const parts = ['tum-ui-table w-full border-collapse text-sm', SIZE_PADDING[this.size()], HEADER_CLASSES, BODY_CLASSES];
        if (this.striped()) {
            parts.push(STRIPED_CLASSES);
        }
        if (this.rowHover()) {
            parts.push(HOVER_CLASSES);
        }
        if (this.scrollable()) {
            parts.push(SCROLLABLE_CLASSES);
        }
        const styleClass = this.styleClass();
        if (styleClass) {
            parts.push(styleClass);
        }
        return parts.join(' ');
    });

    /**
     * Cycle + emit the sort for `field`, reproducing PrimeNG single-sort semantics: clicking the current
     * sort field toggles its order (asc <-> desc); clicking any other field sorts it by `defaultSortOrder`
     * (ascending). Called by {@link TumUiTableSortableColumnComponent} on click / keyboard activation.
     */
    requestSort(field: string): void {
        const order = this.sortField() === field ? this.sortOrder() * -1 : this.defaultSortOrder();
        this.sortChange.emit({ field, order });
    }
}
