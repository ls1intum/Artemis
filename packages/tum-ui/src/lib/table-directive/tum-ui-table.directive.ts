import { Directive, booleanAttribute, computed, input, output } from '@angular/core';

export type TumUiTableSize = 'small' | 'normal' | 'large';

export interface TumUiTableSortEvent {
    field: string;
    order: number;
}

const SIZE_PADDING: Record<TumUiTableSize, string> = {
    small: 'tum:[&_thead_th]:px-2 tum:[&_thead_th]:py-1.5 tum:[&_tbody_td]:px-2 tum:[&_tbody_td]:py-1.5',
    normal: 'tum:[&_thead_th]:px-4 tum:[&_thead_th]:py-3 tum:[&_tbody_td]:px-4 tum:[&_tbody_td]:py-3',
    large: 'tum:[&_thead_th]:px-5 tum:[&_thead_th]:py-4 tum:[&_tbody_td]:px-5 tum:[&_tbody_td]:py-4',
};

const HEADER_CLASSES =
    'tum:[&_thead_th]:text-left tum:[&_thead_th]:font-semibold tum:[&_thead_th]:whitespace-nowrap ' +
    'tum:[&_thead_th]:bg-tum-ui-content-background tum:[&_thead_th]:text-tum-ui-text ' +
    'tum:[&_thead_th]:border-b tum:[&_thead_th]:border-solid tum:[&_thead_th]:border-tum-ui-border';

const BODY_CLASSES = 'tum:[&_tbody_td]:text-tum-ui-text tum:[&_tbody_td]:border-b tum:[&_tbody_td]:border-solid tum:[&_tbody_td]:border-tum-ui-border';

const STRIPED_CLASSES = 'tum:[&_tbody_tr:nth-child(odd)]:bg-tum-ui-table-striped-background';

const HOVER_CLASSES = 'tum:[&_tbody_tr:hover]:bg-tum-ui-hover-background';

const SCROLLABLE_CLASSES = 'tum:[&_thead_th]:sticky tum:[&_thead_th]:top-0 tum:[&_thead_th]:z-10';

@Directive({
    selector: 'table[tumUiTable]',
    host: {
        '[class]': 'hostClasses()',
    },
})
export class TumUiTableDirective {
    readonly size = input<TumUiTableSize>('normal');

    readonly striped = input(false, { transform: booleanAttribute });

    readonly scrollable = input(false, { transform: booleanAttribute });

    readonly rowHover = input(false, { transform: booleanAttribute });

    readonly styleClass = input('');

    readonly sortField = input<string | undefined>(undefined);

    readonly sortOrder = input<number>(1);

    readonly defaultSortOrder = input<number>(1);

    readonly sortChange = output<TumUiTableSortEvent>();

    protected readonly hostClasses = computed(() => {
        const parts = ['tum-ui-table tum:w-full tum:border-collapse tum:text-sm', SIZE_PADDING[this.size()], HEADER_CLASSES, BODY_CLASSES];
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

    requestSort(field: string): void {
        const order = this.sortField() === field ? this.sortOrder() * -1 : this.defaultSortOrder();
        this.sortChange.emit({ field, order });
    }
}
