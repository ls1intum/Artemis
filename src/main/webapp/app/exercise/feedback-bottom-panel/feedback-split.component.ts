import { ChangeDetectionStrategy, Component, OnInit, TemplateRef, computed, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { SplitterModule } from 'primeng/splitter';
import type { SplitterResizeEndEvent } from 'primeng/types/splitter';
import { SPLITTER_GUTTER_SIZE, splitterPassThrough } from 'app/assessment/manage/assessment-workspace/splitter-pass-through';
import { parseJson } from 'app/foundation/util/json.util';

/** The share of the height the upper content and the panel start with, in percent. */
const DEFAULT_SIZES = [68, 32];
/**
 * What the splitter gives the panel while it is collapsed. Not 0: PrimeNG reads a size of 0 as "no size given" and splits the
 * height evenly. The panel keeps the height of its header, see the styles.
 */
const COLLAPSED_SIZES = [99.99, 0.01];

/**
 * Puts a page's content above its feedback panel and lets the user drag the line between them, the same `p-splitter` and handle
 * that separate the panels of the assessment workspace. The text, modeling and programming pages use it, so the general feedback
 * panel is divided from the content alike on all of them.
 *
 * The content goes into `[feedbackSplitTop]`. The panel is a template (`[bottomTemplate]`) rather than projected content: it
 * usually depends on a condition, and a conditional block does not reliably project into a slot that a splitter creates later.
 * Without a panel (`[hasBottom]` is false, e.g. an assessment with no general feedback) the content simply fills the space.
 *
 * The page owns whether the panel is collapsed and passes it as `[expanded]`. A collapsed panel keeps only its header and the
 * line cannot be dragged; expanding restores the height the user had chosen.
 */
@Component({
    selector: 'jhi-feedback-split',
    templateUrl: './feedback-split.component.html',
    styleUrls: ['./feedback-split.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [SplitterModule, NgTemplateOutlet],
})
export class FeedbackSplitComponent implements OnInit {
    /** Whether the panel exists; without it only the upper content is shown. */
    readonly hasBottom = input(true);
    /** The split (percent) the content and the panel start with when the user has not dragged it yet. */
    readonly defaultSizes = input<number[]>(DEFAULT_SIZES);
    /** The smallest share (percent) the content and the panel may be dragged to. */
    readonly minSizes = input<number[]>([30, 8]);
    /** What the lower panel shows, usually a `jhi-feedback-bottom-panel`. */
    readonly bottomTemplate = input<TemplateRef<unknown>>();
    /** Whether the panel shows its content. */
    readonly expanded = input(true);
    /** Where the chosen split is remembered between visits. */
    readonly storageKey = input.required<string>();

    /** The user finished dragging the line between the content and the panel. */
    readonly resizeEnd = output<void>();

    /** The split the user last dragged to, so a panel that was collapsed and is opened again gets it back. */
    private readonly draggedSizes = signal<number[]>(DEFAULT_SIZES);

    protected readonly sizes = computed(() => (this.expanded() ? this.draggedSizes() : COLLAPSED_SIZES));
    protected readonly gutterSize = SPLITTER_GUTTER_SIZE;
    protected readonly passThrough = splitterPassThrough('vertical');

    ngOnInit(): void {
        // PrimeNG restores the saved split on its own and fails on a value it cannot parse, so one that is no usable split is
        // dropped before it starts. The saved split is read here too, so a collapsed panel opens at that height again.
        this.draggedSizes.set(this.readSavedSizes() ?? this.defaultSizes());
    }

    private readSavedSizes(): number[] | undefined {
        const key = this.storageKey();
        try {
            const raw = localStorage.getItem(key);
            if (raw === null) {
                return undefined;
            }
            const parsed = parseJson<unknown>(raw);
            if (Array.isArray(parsed) && parsed.length === 2 && parsed.every((size) => typeof size === 'number' && size > 0)) {
                return parsed;
            }
        } catch {
            // Not JSON, or storage is blocked: fall through to the default split
        }
        try {
            localStorage.removeItem(key);
        } catch {
            // Storage is blocked; there is nothing to clean up
        }
        return undefined;
    }

    protected onResizeEnd(event: SplitterResizeEndEvent): void {
        // The size of a collapsed panel is not a choice of the user
        if (this.expanded()) {
            this.draggedSizes.set(event.sizes.map(Number));
        }
        this.resizeEnd.emit();
    }
}
