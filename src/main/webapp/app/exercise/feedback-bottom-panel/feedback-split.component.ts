import { ChangeDetectionStrategy, Component, ElementRef, Injector, OnInit, TemplateRef, afterNextRender, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { map } from 'rxjs';
import { NgTemplateOutlet } from '@angular/common';
import { TumAetUiSplitterComponent } from '@tumaet/ui-angular/splitter';
import { parseJson } from 'app/foundation/util/json.util';

/** The share of the height the upper content and the panel start with, in percent. */
const DEFAULT_SIZES = [68, 32];

/**
 * Puts a page's content above its feedback panel and lets the user drag the line between them, the same splitter that separates the
 * panels of the assessment workspace. The text, modeling and programming pages use it, so the general feedback panel is divided from
 * the content alike on all of them.
 *
 * The content goes into `[feedbackSplitTop]`. The panel is a template (`[bottomTemplate]`) rather than projected content: it
 * usually depends on a condition, and a conditional block does not reliably project into a slot that a splitter creates later.
 * Without a panel (`[hasBottom]` is false, e.g. an assessment with no general feedback) the content simply fills the space.
 *
 * The page owns whether the panel is collapsed and passes it as `[expanded]`. A collapsed panel keeps only its header and the
 * line cannot be dragged; expanding restores the height the user had chosen.
 *
 * Dragging the line until the panel is no taller than `[collapseSnapPercent]` asks the page to collapse it (`collapseRequested`), if the
 * page sets one: by default the height is only limited, and the panel collapses from its button. The page answers by setting `[expanded]`
 * to false; the height the user had before the drag is what the panel reopens with.
 *
 * Up to 992px the exercise page no longer gives the split a fixed height (see the resizeable container), so the content and the
 * panel are stacked in the scrolling page instead.
 */
@Component({
    selector: 'jhi-feedback-split',
    templateUrl: './feedback-split.component.html',
    styleUrls: ['./feedback-split.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumAetUiSplitterComponent, NgTemplateOutlet],
    host: { '[class.feedback-split--stacked]': 'stacked()' },
})
export class FeedbackSplitComponent implements OnInit {
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly injector = inject(Injector);

    /** Whether the content and the panel are stacked rather than split, at the width where the page stops giving a fixed height. */
    protected readonly stacked = toSignal(
        inject(BreakpointObserver)
            .observe('(max-width: 992px)')
            .pipe(map((state) => state.matches)),
        { initialValue: false },
    );

    /** Whether the panel exists; without it only the upper content is shown. */
    readonly hasBottom = input(true);
    /** The split (percent) the content and the panel start with when the user has not dragged it yet. */
    readonly defaultSizes = input<number[]>(DEFAULT_SIZES);
    /** The smallest share (percent) the content and the panel may be dragged to. */
    readonly minSizes = input<number[]>([30, 15]);
    /** What the lower panel shows, usually a `jhi-feedback-bottom-panel`. */
    readonly bottomTemplate = input<TemplateRef<unknown>>();
    /** Whether the panel shows its content. */
    readonly expanded = input(true);
    /**
     * The share (percent) of the height at or below which dragging the panel smaller collapses it. Must stay above the panel's
     * smallest share in `[minSizes]`, which the drag cannot go below. 0, the default, turns the drag-to-collapse off: the panel is
     * only limited in height, and collapses from its button. Dragging it shut is for panels that are sized along the width.
     */
    readonly collapseSnapPercent = input(0);
    /** Where the chosen split is remembered between visits. */
    readonly storageKey = input.required<string>();

    /** The user finished dragging the line between the content and the panel. */
    readonly resizeEnd = output<void>();
    /** The user dragged the panel down to its smallest size, so the page should collapse it. */
    readonly collapseRequested = output<void>();

    /**
     * The split the user last dragged to, so a panel that was collapsed and is opened again gets it back. The splitter keeps a drag
     * until this input changes, so setting it to a new array puts the splitter back at this split.
     */
    protected readonly draggedSizes = signal<number[]>(DEFAULT_SIZES);

    ngOnInit(): void {
        this.draggedSizes.set(this.readSavedSizes() ?? this.defaultSizes());
    }

    private readSavedSizes(): number[] | undefined {
        try {
            const raw = localStorage.getItem(this.storageKey());
            const parsed = raw === null ? undefined : parseJson<unknown>(raw);
            if (Array.isArray(parsed) && parsed.length === 2 && parsed.every((size) => typeof size === 'number' && size > 0)) {
                return parsed;
            }
        } catch {
            // Not JSON, or storage is blocked: the default split applies
        }
        return undefined;
    }

    protected onResizeEnd(sizes: number[]): void {
        if (this.collapseSnapPercent() > 0 && sizes[1] <= this.collapseSnapPercent()) {
            // Not kept as the chosen split, so the panel reopens at a usable height rather than as the dragged sliver
            this.draggedSizes.set([...this.draggedSizes()]);
            const separatorFocused = this.host.nativeElement.contains(document.activeElement);
            this.collapseRequested.emit();
            if (separatorFocused) {
                // The separator is hidden once the panel collapses, so the focus moves to the control that opens the panel again
                afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('.feedback-split__bottom .collapsed-panel-rail__button')?.focus(), {
                    injector: this.injector,
                });
            }
        } else {
            this.draggedSizes.set(sizes);
            this.saveSizes(sizes);
        }
        this.resizeEnd.emit();
    }

    private saveSizes(sizes: number[]): void {
        try {
            localStorage.setItem(this.storageKey(), JSON.stringify(sizes));
        } catch {
            // Storage is blocked; the remembered split still applies for this visit
        }
    }
}
