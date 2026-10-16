import { Directive, booleanAttribute, computed, input, numberAttribute, output } from '@angular/core';

export type TumAetUiTableSize = 'small' | 'normal' | 'large';

export interface TumAetUiTableSortEvent {
    field: string;
    order: number;
}

const SIZE_PADDING: Record<TumAetUiTableSize, string> = {
    small: 'tumaet:[&_thead_th]:px-2 tumaet:[&_thead_th]:py-1.5 tumaet:[&_tbody_td]:px-2 tumaet:[&_tbody_td]:py-1.5',
    normal: 'tumaet:[&_thead_th]:px-4 tumaet:[&_thead_th]:py-3 tumaet:[&_tbody_td]:px-4 tumaet:[&_tbody_td]:py-3',
    large: 'tumaet:[&_thead_th]:px-5 tumaet:[&_thead_th]:py-4 tumaet:[&_tbody_td]:px-5 tumaet:[&_tbody_td]:py-4',
};

const HEADER_CLASSES =
    'tumaet:[&_thead_th]:text-start tumaet:[&_thead_th]:font-semibold tumaet:[&_thead_th]:whitespace-nowrap ' +
    'tumaet:[&_thead_th]:bg-content-background tumaet:[&_thead_th]:text-text ' +
    'tumaet:[&_thead_th]:border-b tumaet:[&_thead_th]:border-border';

const BODY_CLASSES = 'tumaet:[&_tbody_td]:text-text tumaet:[&_tbody_td]:border-b tumaet:[&_tbody_td]:border-border';

const STRIPED_CLASSES = 'tumaet:[&_tbody_tr:nth-child(odd)]:bg-table-striped-background';

const HOVER_CLASSES = 'tumaet:[&_tbody_tr:hover]:bg-hover-background';

const SCROLLABLE_CLASSES = 'tumaet:[&_thead_th]:sticky tumaet:[&_thead_th]:top-0 tumaet:[&_thead_th]:z-10';

@Directive({
    selector: 'table[tumAetUiTable]',
    host: {
        '[class]': 'hostClasses()',
    },
})
export class TumAetUiTableDirective {
    readonly size = input<TumAetUiTableSize>('normal');

    readonly striped = input(false, { transform: booleanAttribute });

    readonly scrollable = input(false, { transform: booleanAttribute });

    readonly rowHover = input(false, { transform: booleanAttribute });

    readonly sortField = input<string | undefined>(undefined);

    readonly sortOrder = input(1, { transform: numberAttribute });

    readonly defaultSortOrder = input(1, { transform: numberAttribute });

    readonly sortChange = output<TumAetUiTableSortEvent>();

    protected readonly hostClasses = computed(() => {
        const parts = ['tumaet-ui-table tumaet:w-full tumaet:border-collapse tumaet:text-sm', SIZE_PADDING[this.size()], HEADER_CLASSES, BODY_CLASSES];
        if (this.striped()) {
            parts.push(STRIPED_CLASSES);
        }
        if (this.rowHover()) {
            parts.push(HOVER_CLASSES);
        }
        if (this.scrollable()) {
            parts.push(SCROLLABLE_CLASSES);
        }
        return parts.join(' ');
    });

    requestSort(field: string): void {
        const order = this.sortField() === field ? this.sortOrder() * -1 : this.defaultSortOrder();
        this.sortChange.emit({ field, order });
    }
}
