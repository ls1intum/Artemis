import { NgTemplateOutlet } from '@angular/common';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { ChangeDetectionStrategy, Component, TemplateRef, TrackByFunction, booleanAttribute, computed, input } from '@angular/core';
import { TumUiTableSize } from './tum-ui-table.directive';

const HEADER_PADDING: Record<TumUiTableSize, string> = {
    small: 'tum:px-2 tum:py-1.5',
    normal: 'tum:px-4 tum:py-3',
    large: 'tum:px-5 tum:py-4',
};

/** Fixed-row-height virtual table for large in-memory collections. */
@Component({
    selector: 'tum-ui-table-virtual-scroll',
    templateUrl: './tum-ui-table-virtual-scroll.component.html',
    styleUrl: './tum-ui-table-virtual-scroll.component.scss',
    imports: [ScrollingModule, NgTemplateOutlet],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTableVirtualScrollComponent<T> {
    readonly items = input.required<readonly T[]>();

    /** Row height in CSS pixels used by the CDK fixed-size virtual-scroll strategy. */
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
    protected readonly viewportHeight = computed(() => (this.isFlexHeight() ? undefined : this.scrollHeight()));
    protected readonly effectiveTrackBy = computed<TrackByFunction<T>>(() => this.trackBy() ?? ((_, item) => item));

    protected readonly headerClasses = computed(() => {
        const base = 'tum-ui-vs-header tum:box-border tum:flex tum:text-sm tum:font-semibold tum:text-text tum:bg-content-background ' + 'tum:border-b tum:border-border';
        return `${base} ${HEADER_PADDING[this.size()]}`;
    });

    protected readonly rowClasses = computed(() => {
        const base = 'tum-ui-vs-row tum:box-border tum:flex tum:items-center tum:text-sm tum:text-text tum:border-b tum:border-border';
        const hover = this.rowHover() ? ' tum:hover:bg-hover-background' : '';
        return `${base}${hover} ${HEADER_PADDING[this.size()]}`;
    });
    protected stripeClass(index: number): string {
        return this.striped() && index % 2 === 0 ? ' tum:bg-table-striped-background' : '';
    }
}
