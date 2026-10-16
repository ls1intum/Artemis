import { ChangeDetectionStrategy, Component, booleanAttribute, computed, inject, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiTableDirective } from './tumaet-ui-table.directive';

type TumAetUiSortDirection = 'asc' | 'desc' | 'none';

@Component({
    selector: 'th[tumAetUiSortableColumn]',
    templateUrl: './tumaet-ui-table-sortable-column.component.html',
    styleUrl: './tumaet-ui-table-sortable-column.component.scss',
    imports: [FaIconComponent],
    host: {
        '[class]': 'hostClasses()',
        '[attr.aria-sort]': 'ariaSort()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTableSortableColumnComponent {
    private readonly table = inject(TumAetUiTableDirective);

    readonly field = input.required<string>({ alias: 'tumAetUiSortableColumn' });

    readonly disabled = input(false, { transform: booleanAttribute });

    protected readonly direction = computed<TumAetUiSortDirection>(() => {
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
    protected readonly sortIcon = computed(() => {
        switch (this.direction()) {
            case 'asc':
                return faSortUp;
            case 'desc':
                return faSortDown;
            default:
                return faSort;
        }
    });

    protected readonly hostClasses = computed(() => (this.disabled() ? '' : 'tumaet:cursor-pointer tumaet:select-none tumaet:hover:bg-hover-background'));

    protected onActivate(): void {
        this.table.requestSort(this.field());
    }
}
