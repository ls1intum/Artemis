import { NgTemplateOutlet } from '@angular/common';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { ChangeDetectionStrategy, Component, TemplateRef, TrackByFunction, booleanAttribute, computed, input } from '@angular/core';
import { TumUiTableSize } from './tum-ui-table.directive';

const HEADER_PADDING: Record<TumUiTableSize, string> = {
    small: 'px-2 py-1.5',
    normal: 'px-4 py-3',
    large: 'px-5 py-4',
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
    protected stripeClass(index: number): string {
        return this.striped() && index % 2 === 0 ? ' bg-tum-ui-surface-50 dark:bg-tum-ui-surface-950' : '';
    }
}
