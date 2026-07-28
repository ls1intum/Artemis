import { Directive, booleanAttribute, computed, input, output } from '@angular/core';

export type TumUiTableSize = 'small' | 'normal' | 'large';

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

    requestSort(field: string): void {
        const order = this.sortField() === field ? this.sortOrder() * -1 : this.defaultSortOrder();
        this.sortChange.emit({ field, order });
    }
}
