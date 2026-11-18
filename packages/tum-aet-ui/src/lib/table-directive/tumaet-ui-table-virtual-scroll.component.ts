import { NgTemplateOutlet } from '@angular/common';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { ChangeDetectionStrategy, Component, TemplateRef, TrackByFunction, booleanAttribute, computed, input } from '@angular/core';
import { TumAetUiTableSize } from './tumaet-ui-table.directive';

const HEADER_PADDING: Record<TumAetUiTableSize, string> = {
    small: 'tumaet:px-2 tumaet:py-1.5',
    normal: 'tumaet:px-4 tumaet:py-3',
    large: 'tumaet:px-5 tumaet:py-4',
};

/** Fixed-row-height virtual table for large in-memory collections. */
@Component({
    selector: 'tumaet-ui-table-virtual-scroll',
    templateUrl: './tumaet-ui-table-virtual-scroll.component.html',
    styleUrl: './tumaet-ui-table-virtual-scroll.component.scss',
    imports: [ScrollingModule, NgTemplateOutlet],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTableVirtualScrollComponent<T> {
    readonly items = input.required<readonly T[]>();

    /** Row height in CSS pixels used by the CDK fixed-size virtual-scroll strategy. */
    readonly itemSize = input.required<number>();

    readonly rowTemplate = input.required<TemplateRef<{ $implicit: T; index: number }>>();

    readonly size = input<TumAetUiTableSize>('normal');

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
        const base =
            'tumaet-ui-vs-header tumaet:box-border tumaet:flex tumaet:text-sm tumaet:font-semibold tumaet:text-text tumaet:bg-content-background ' +
            'tumaet:border-b tumaet:border-border';
        return `${base} ${HEADER_PADDING[this.size()]}`;
    });

    protected readonly rowClasses = computed(() => {
        const base = 'tumaet-ui-vs-row tumaet:box-border tumaet:flex tumaet:items-center tumaet:text-sm tumaet:text-text tumaet:border-b tumaet:border-border';
        const hover = this.rowHover() ? ' tumaet:hover:bg-hover-background' : '';
        return `${base}${hover} ${HEADER_PADDING[this.size()]}`;
    });
    protected stripeClass(index: number): string {
        return this.striped() && index % 2 === 0 ? ' tumaet:bg-table-striped-background' : '';
    }
}
