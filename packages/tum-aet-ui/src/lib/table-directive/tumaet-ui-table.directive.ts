import { Directive, booleanAttribute, computed, input, numberAttribute, output } from '@angular/core';

export type TumAetUiTableSize = 'small' | 'normal' | 'large';

export interface TumAetUiTableSortEvent {
    field: string;
    order: number;
}

/** The size only changes the body cells: every column header is 30px high and horizontally padded like its cells. */
const SIZE_PADDING: Record<TumAetUiTableSize, string> = {
    small: 'tumaet:[&>thead_th]:px-2 tumaet:[&_tbody_td]:px-2 tumaet:[&_tbody_td]:py-1.5',
    normal: 'tumaet:[&>thead_th]:px-4 tumaet:[&_tbody_td]:px-4 tumaet:[&_tbody_td]:py-3',
    large: 'tumaet:[&>thead_th]:px-5 tumaet:[&_tbody_td]:px-5 tumaet:[&_tbody_td]:py-4',
};

const HEADER_CLASSES =
    'tumaet:[&>thead_th]:h-[30px] tumaet:[&>thead_th]:py-0 tumaet:[&>thead_th]:text-start tumaet:[&>thead_th]:font-semibold tumaet:[&>thead_th]:whitespace-nowrap ' +
    'tumaet:[&>thead_th]:sticky tumaet:[&>thead_th]:top-0 tumaet:[&>thead_th]:z-10 ' +
    'tumaet:[&>thead_th]:bg-content-background tumaet:[&>thead_th]:text-text ' +
    'tumaet:[&>thead_th]:border-b tumaet:[&>thead_th]:border-border';

const BODY_CLASSES = 'tumaet:[&_tbody_td]:text-text tumaet:[&_tbody_td]:border-b tumaet:[&_tbody_td]:border-border';

const STRIPED_CLASSES = 'tumaet:[&_tbody_tr:nth-child(odd)]:bg-table-striped-background';

const HOVER_CLASSES = 'tumaet:[&_tbody_tr:hover]:bg-hover-background';

@Directive({
    selector: 'table[tumAetUiTable]',
    host: {
        '[class]': 'hostClasses()',
    },
})
export class TumAetUiTableDirective {
    readonly size = input<TumAetUiTableSize>('normal');

    readonly striped = input(false, { transform: booleanAttribute });

    readonly rowHover = input(false, { transform: booleanAttribute });

    readonly sortField = input<string | undefined>(undefined);

    readonly sortOrder = input(1, { transform: numberAttribute });

    readonly defaultSortOrder = input(1, { transform: numberAttribute });

    readonly sortChange = output<TumAetUiTableSortEvent>();

    protected readonly hostClasses = computed(() => {
        const parts = ['tumaet-ui-table tumaet:w-full tumaet:border-separate tumaet:border-spacing-0 tumaet:text-sm', SIZE_PADDING[this.size()], HEADER_CLASSES, BODY_CLASSES];
        if (this.striped()) {
            parts.push(STRIPED_CLASSES);
        }
        if (this.rowHover()) {
            parts.push(HOVER_CLASSES);
        }
        return parts.join(' ');
    });

    requestSort(field: string): void {
        const order = this.sortField() === field ? this.sortOrder() * -1 : this.defaultSortOrder();
        this.sortChange.emit({ field, order });
    }
}
