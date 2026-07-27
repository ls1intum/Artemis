import { ChangeDetectionStrategy, Component, booleanAttribute, computed, inject, input } from '@angular/core';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';

type TumUiSortDirection = 'asc' | 'desc' | 'none';

/**
 * Sortable header-cell for {@link TumUiTableDirective}, part of the tum-aet-ui kit.
 *
 * Applied to a `<th>` inside a `[tumUiTable]`, it is a PrimeNG-free drop-in for `pSortableColumn` +
 * `<p-sortIcon>` + `[customSort]`: it makes the header clickable / keyboard-operable, exposes `aria-sort`,
 * and appends the exact three-state Aura sort icon after the header text (the icon SVGs are copied verbatim
 * from the existing tum-ui-table so the two look identical). It is a component (not a bare directive) purely
 * so it can template the appended icon after the projected header content.
 *
 * The parent {@link TumUiTableDirective} owns the sort state: this component reads its controlled
 * `sortField`/`sortOrder` to render the icon + `aria-sort`, and calls `requestSort()` on activation, which
 * emits the table's `sortChange`. Nothing sorts locally — parity with p-table's `[customSort]` mode.
 */
@Component({
    selector: 'th[tumUiSortableColumn]',
    templateUrl: './tum-ui-table-sortable-column.component.html',
    styleUrl: './tum-ui-table-sortable-column.component.scss',
    host: {
        '[class]': 'hostClasses()',
        '[attr.aria-sort]': 'ariaSort()',
        '[attr.tabindex]': 'disabled() ? null : "0"',
        '(click)': 'onActivate()',
        '(keydown.enter)': 'onKeyActivate($event)',
        '(keydown.space)': 'onKeyActivate($event)',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTableSortableColumnComponent {
    /** Column field name to sort by (drop-in for `pSortableColumn="field"` / `<p-sortIcon field="field">`). */
    readonly field = input.required<string>({ alias: 'tumUiSortableColumn' });
    /** Disable sorting for this column (parity with `pSortableColumnDisabled`); use as `[disabled]="true"`. */
    readonly disabled = input(false, { transform: booleanAttribute });

    private readonly table = inject(TumUiTableDirective);

    /** Current sort direction of this column, derived from the table's controlled sort state. */
    protected readonly direction = computed<TumUiSortDirection>(() => {
        if (this.table.sortField() !== this.field()) {
            return 'none';
        }
        const order = this.table.sortOrder();
        if (order === 0) {
            return 'none';
        }
        return order < 0 ? 'desc' : 'asc';
    });

    protected readonly ariaSort = computed<'ascending' | 'descending' | 'none'>(() => {
        switch (this.direction()) {
            case 'asc':
                return 'ascending';
            case 'desc':
                return 'descending';
            default:
                return 'none';
        }
    });

    // Enabled columns get the pointer + hover affordance p-table's sortable headers have; disabled ones stay inert.
    protected readonly hostClasses = computed(() => (this.disabled() ? '' : 'cursor-pointer select-none hover:bg-surface-100 dark:hover:bg-surface-800'));

    protected onActivate(): void {
        if (!this.disabled()) {
            this.table.requestSort(this.field());
        }
    }

    protected onKeyActivate(event: Event): void {
        if (this.disabled()) {
            return;
        }
        // Prevent the space key from scrolling the page / enter from submitting an enclosing form.
        event.preventDefault();
        this.table.requestSort(this.field());
    }
}
