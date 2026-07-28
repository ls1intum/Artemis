import { NgTemplateOutlet } from '@angular/common';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { ChangeDetectionStrategy, Component, TemplateRef, TrackByFunction, booleanAttribute, computed, input } from '@angular/core';
import { TumUiTableSize } from './tum-ui-table.directive';

const HEADER_PADDING: Record<TumUiTableSize, string> = {
    small: 'tum:px-2 tum:py-1.5',
    normal: 'tum:px-4 tum:py-3',
    large: 'tum:px-5 tum:py-4',
};

@Component({
    selector: 'tum-ui-table-virtual-scroll',
    templateUrl: './tum-ui-table-virtual-scroll.component.html',
    styleUrl: './tum-ui-table-virtual-scroll.component.scss',
    imports: [ScrollingModule, NgTemplateOutlet],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTableVirtualScrollComponent<T> {
    readonly items = input.required<readonly T[]>();

    /** Fixed row height in pixels; the rendered row uses this value as its CSS height. */
    readonly itemSize = input.required<number>();

    readonly rowTemplate = input.required<TemplateRef<{ $implicit: T; index: number }>>();

    readonly size = input<TumUiTableSize>('normal');

    readonly striped = input(false, { transform: booleanAttribute });

    readonly rowHover = input(false, { transform: booleanAttribute });

    readonly scrollHeight = input<string>('flex');

    readonly minWidth = input<string | undefined>(undefined);

    readonly trackBy = input<TrackByFunction<T> | undefined>(undefined);
    readonly ariaDescribedBy = input<string | undefined>(undefined);

    protected readonly isFlexHeight = computed(() => this.scrollHeight() === 'flex');
    protected readonly maxHeight = computed(() => (this.isFlexHeight() ? undefined : this.scrollHeight()));
    protected readonly effectiveTrackBy = computed<TrackByFunction<T>>(() => this.trackBy() ?? ((_, item) => item));

    protected readonly headerClasses = computed(() => {
        const base =
            'tum-ui-vs-header tum:flex tum:text-sm tum:font-semibold tum:text-tum-ui-surface-700 tum:bg-tum-ui-surface-0 tum:dark:text-tum-ui-surface-0 tum:dark:bg-tum-ui-surface-900 ' +
            'tum:border-b tum:border-solid tum:border-tum-ui-surface-200 tum:dark:border-tum-ui-surface-800';
        return `${base} ${HEADER_PADDING[this.size()]}`;
    });

    protected readonly rowClasses = computed(() => {
        const base =
            'tum-ui-vs-row tum:flex tum:items-center tum:text-sm tum:text-tum-ui-surface-900 tum:dark:text-tum-ui-surface-0 tum:border-b tum:border-solid tum:border-tum-ui-surface-200 tum:dark:border-tum-ui-surface-800';
        const hover = this.rowHover() ? ' tum:hover:bg-tum-ui-surface-100 tum:dark:hover:bg-tum-ui-surface-800' : '';
        return `${base}${hover} ${HEADER_PADDING[this.size()]}`;
    });
    protected stripeClass(index: number): string {
        return this.striped() && index % 2 === 0 ? ' tum:bg-tum-ui-surface-50 tum:dark:bg-tum-ui-surface-950' : '';
    }
}
