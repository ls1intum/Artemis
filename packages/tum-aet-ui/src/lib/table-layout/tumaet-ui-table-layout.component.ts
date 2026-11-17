import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterNextRender, booleanAttribute, computed, inject, input, signal } from '@angular/core';
import { TumAetUiEmptyStateComponent } from '../empty-state/tumaet-ui-empty-state.component';
import { TumAetUiTranslatePipe } from '../i18n/tumaet-ui-translate.pipe';
import { TumAetUiProgressSpinnerComponent } from '../progress-spinner/tumaet-ui-progress-spinner.component';

/**
 * The measured frame never gets smaller than this: toolbar, header, about eight rows and the footer. A table far down a
 * scrolling page (below other tables or forms) then stays usable and the page scrolls instead of squeezing the rows.
 */
const MIN_HEIGHT_PX = 320;
/** Space kept free below the frame inside its scroll container. */
const BOTTOM_GAP_PX = 12;
const OFFSET_PROPERTY = '--table-layout-offset';

function scrollParent(element: HTMLElement): HTMLElement | undefined {
    for (let parent = element.parentElement; parent; parent = parent.parentElement) {
        const overflowY = getComputedStyle(parent).overflowY;
        if (overflowY === 'auto' || overflowY === 'scroll') {
            return parent;
        }
    }
    return undefined;
}

/**
 * Frame shared by every data table: a sticky toolbar of at most 40px, a body that is the only part that scrolls
 * (with the 30px column header of `table[tumAetUiTable]` stuck to its top) and a sticky footer of at most 40px.
 *
 * ```html
 * <tumaet-ui-table-layout [loading]="loading()">
 *     <div tumAetUiTableToolbar>search, filters, actions</div>
 *     <table tumAetUiTable>…</table>
 *     <tumaet-ui-paginator tumAetUiTableFooter … />
 * </tumaet-ui-table-layout>
 * ```
 *
 * Empty: bind `empty` to "no rows and not loading" and the body shows a centered empty state under the column header. Set
 * `emptyTitle` and `emptyDescription` (translation keys) for the page's wording and project actions such as "Create" with
 * `[tumAetUiTableEmptyActions]`.
 *
 * Do not wrap a slotted node in `@if`: with preserved whitespace Angular no longer projects it (NG8011). Set `[hidden]` on
 * the node instead, or put the `@if` inside it.
 *
 * Height: the frame is as tall as its table, but never taller than the space left below it in its scroll container, so a
 * long table keeps toolbar and footer in view whatever sits above it. The frame measures its own position and follows
 * resizes. Where that cannot work (several frames stacked on one page, or a container without a definite height), set
 * `--table-layout-offset` (the height of everything around the table, a CSS length) on the frame or an ancestor to
 * switch to a viewport based cap, or give the frame a height with `fill` when a parent already provides one.
 */
@Component({
    selector: 'tumaet-ui-table-layout',
    templateUrl: './tumaet-ui-table-layout.component.html',
    styleUrl: './tumaet-ui-table-layout.component.scss',
    imports: [TumAetUiEmptyStateComponent, TumAetUiProgressSpinnerComponent, TumAetUiTranslatePipe],
    host: {
        '[class.tumaet-ui-table-layout-fill]': 'fill()',
        '[attr.aria-busy]': "loading() ? 'true' : null",
        '[style.max-height]': 'maxHeight()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTableLayoutComponent {
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    private readonly destroyRef = inject(DestroyRef);

    /** Dims the body and shows a spinner while the consumer loads rows. */
    readonly loading = input(false, { transform: booleanAttribute });
    /** Takes the height of a flex-column parent instead of sizing to the table. */
    readonly fill = input(false, { transform: booleanAttribute });
    /** Shows the empty state in the body, under the column header, instead of leaving it blank. Bind it to "no rows and not loading". */
    readonly empty = input(false, { transform: booleanAttribute });
    /** Translation key of the empty state heading. */
    readonly emptyTitle = input('tumAetUi.table.empty');
    /** Optional translation key of a sentence below the heading, for example how to add the first entry. */
    readonly emptyDescription = input<string | undefined>(undefined);

    private readonly measuredHeight = signal<number | undefined>(undefined);
    protected readonly maxHeight = computed(() => {
        const measured = this.measuredHeight();
        return measured === undefined || this.fill() ? null : `${Math.max(MIN_HEIGHT_PX, measured)}px`;
    });

    constructor() {
        afterNextRender(() => this.observeAvailableHeight());
    }

    /** An explicit offset, set by the page on the frame or an ancestor, keeps the viewport based cap of the stylesheet. */
    private hasExplicitOffset(): boolean {
        return getComputedStyle(this.host).getPropertyValue(OFFSET_PROPERTY).trim() !== '';
    }

    private observeAvailableHeight(): void {
        // A missing observer is a test environment.
        if (typeof ResizeObserver === 'undefined') {
            return;
        }
        const container = scrollParent(this.host);
        // Checked on every measurement, not once: styles of a frame that is created inside an overlay may not be resolved yet
        // when it renders for the first time, and the later resize that follows must not keep a measured cap.
        const measure = () => this.measuredHeight.set(this.hasExplicitOffset() ? undefined : this.availableHeight(container));
        const observer = new ResizeObserver(measure);
        observer.observe(container ?? document.documentElement);
        if (this.host.parentElement) {
            observer.observe(this.host.parentElement);
        }
        this.destroyRef.onDestroy(() => observer.disconnect());
        measure();
    }

    /** Height from the top of the frame, as laid out without scrolling, to the bottom of its scroll container. */
    private availableHeight(container: HTMLElement | undefined): number {
        const top = this.host.getBoundingClientRect().top;
        if (container) {
            return Math.floor(container.clientHeight - (top - container.getBoundingClientRect().top + container.scrollTop) - BOTTOM_GAP_PX);
        }
        return Math.floor(window.innerHeight - (top + window.scrollY) - BOTTOM_GAP_PX);
    }
}
