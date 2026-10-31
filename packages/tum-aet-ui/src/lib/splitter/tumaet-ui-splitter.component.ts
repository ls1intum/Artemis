import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    Directive,
    ElementRef,
    Renderer2,
    TemplateRef,
    computed,
    contentChildren,
    effect,
    inject,
    input,
    linkedSignal,
    numberAttribute,
    output,
} from '@angular/core';

/** Declares one of exactly two persistent splitter panels, in document order. */
@Directive({ selector: 'ng-template[tumAetUiSplitterPanel]' })
export class TumAetUiSplitterPanelDirective {
    readonly template = inject(TemplateRef<unknown>);
}

/**
 * Two persistent panels separated by a keyboard and pointer operated separator.
 * Sizes and minimums are percentages of the available space excluding the gutter.
 * Arrow keys adjust by one percentage point (ten with Shift); Home/End reach the minimum/maximum.
 * Horizontal places panels side by side; vertical stacks them. Persistence belongs to the consumer.
 */
@Component({
    selector: 'tumaet-ui-splitter',
    imports: [NgTemplateOutlet],
    templateUrl: './tumaet-ui-splitter.component.html',
    styleUrl: './tumaet-ui-splitter.component.scss',
    host: {
        '[class.vertical]': 'orientation() === "vertical"',
        '[style.flex-direction]': 'orientation() === "vertical" ? "column" : "row"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiSplitterComponent {
    private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly document = inject(DOCUMENT);
    private readonly destroyRef = inject(DestroyRef);
    private readonly renderer = inject(Renderer2);

    readonly orientation = input<'horizontal' | 'vertical'>('horizontal');
    /** Two nonnegative relative weights, normalized to 100 and clamped to minSizes. */
    readonly sizes = input<readonly number[]>([50, 50]);
    /** Minimum percentages. Impossible minimums whose sum exceeds 100 are proportionally normalized. */
    readonly minSizes = input<readonly number[]>([0, 0]);
    readonly gutterSize = input(8, { transform: numberAttribute });
    readonly separatorLabel = input.required<string>();
    /** Fix the second panel to a CSS size and hide the separator while retaining both panel views. */
    readonly collapsedSecondSize = input<string>();
    readonly sizesChange = output<number[]>();
    readonly resizeEnd = output<number[]>();

    protected readonly panels = contentChildren(TumAetUiSplitterPanelDirective);
    protected readonly minimums = computed(() => {
        const first = this.percentage(this.minSizes()[0]);
        const second = this.percentage(this.minSizes()[1]);
        const sum = first + second;
        return sum > 100 ? [(first * 100) / sum, (second * 100) / sum] : [first, second];
    });
    protected readonly maximum = computed(() => 100 - this.minimums()[1]);
    protected readonly current = linkedSignal(() => {
        const [first, second] = this.sizes();
        const sum = first + second;
        return this.clamp(Number.isFinite(sum) && first >= 0 && second >= 0 && sum > 0 ? (first * 100) / sum : 50);
    });
    protected readonly safeGutterSize = computed(() => Math.max(0, Number.isFinite(this.gutterSize()) ? this.gutterSize() : 8));
    protected readonly firstBasis = computed(() => (this.collapsedSecondSize() ? undefined : `calc((100% - ${this.safeGutterSize()}px) * ${this.current() / 100})`));
    private dragCleanup?: () => void;

    constructor() {
        this.destroyRef.onDestroy(() => this.dragCleanup?.());
        effect(() => {
            // A responsive/controlled update during a gesture must not leave a viewport shield behind.
            this.orientation();
            this.minSizes();
            this.collapsedSecondSize();
            this.dragCleanup?.();
        });
    }

    protected onKeydown(event: KeyboardEvent): void {
        if (this.collapsedSecondSize()) {
            return;
        }
        const increase = this.orientation() === 'horizontal' ? 'ArrowRight' : 'ArrowDown';
        const decrease = this.orientation() === 'horizontal' ? 'ArrowLeft' : 'ArrowUp';
        let value: number;
        switch (event.key) {
            case 'Home':
                value = this.minimums()[0];
                break;
            case 'End':
                value = this.maximum();
                break;
            default:
                if (event.key !== increase && event.key !== decrease) {
                    return;
                }
                value = this.current() + (event.key === increase ? 1 : -1) * (event.shiftKey ? 10 : 1);
        }
        event.preventDefault();
        this.update(value);
        this.resizeEnd.emit(this.values());
    }

    protected onPointerdown(event: PointerEvent): void {
        if (event.button !== 0 || this.collapsedSecondSize()) {
            return;
        }
        event.preventDefault();
        this.dragCleanup?.();
        const horizontal = this.orientation() === 'horizontal';
        const bounds = this.element.nativeElement.getBoundingClientRect();
        const available = (horizontal ? bounds.width : bounds.height) - this.safeGutterSize();
        if (available <= 0) {
            return;
        }
        const start = horizontal ? event.clientX : event.clientY;
        const previous = this.current();
        const pointerId = event.pointerId;
        const separator = event.currentTarget as HTMLElement;
        separator.focus();
        separator.setPointerCapture?.(pointerId);
        // Pointer capture plus a viewport shield keeps embedded PDF/video frames from swallowing the gesture.
        const shield = this.document.createElement('div');
        shield.setAttribute('data-tumaet-splitter-shield', '');
        shield.setAttribute('aria-hidden', 'true');
        shield.style.cssText = `position:fixed;inset:0;z-index:2147483647;touch-action:none;user-select:none;cursor:${horizontal ? 'col-resize' : 'row-resize'}`;
        this.document.body.appendChild(shield);
        const listeners: (() => void)[] = [];
        const cleanup = () => {
            listeners.forEach((remove) => remove());
            shield.remove();
            if (separator.hasPointerCapture?.(pointerId)) {
                separator.releasePointerCapture(pointerId);
            }
            this.dragCleanup = undefined;
        };
        this.dragCleanup = cleanup;
        const cancel = () => {
            cleanup();
            this.update(previous);
        };

        listeners.push(
            this.renderer.listen(this.document, 'pointermove', (move: PointerEvent) => {
                if (move.pointerId === pointerId) {
                    this.update(previous + (((horizontal ? move.clientX : move.clientY) - start) * 100) / available);
                }
            }),
        );
        listeners.push(
            this.renderer.listen(this.document, 'pointerup', (end: PointerEvent) => {
                if (end.pointerId === pointerId) {
                    cleanup();
                    this.resizeEnd.emit(this.values());
                }
            }),
        );
        listeners.push(this.renderer.listen(this.document, 'pointercancel', cancel));
        listeners.push(this.renderer.listen(separator, 'lostpointercapture', cancel));
        if (this.document.defaultView) {
            listeners.push(this.renderer.listen(this.document.defaultView, 'blur', cancel));
        }
    }

    private percentage(value: number | undefined): number {
        return Number.isFinite(value) ? Math.max(0, Math.min(100, value!)) : 0;
    }

    private clamp(value: number): number {
        return Math.max(this.minimums()[0], Math.min(this.maximum(), value));
    }

    private values(): number[] {
        return [this.current(), 100 - this.current()];
    }

    private update(value: number): void {
        this.current.set(this.clamp(value));
        this.sizesChange.emit(this.values());
    }
}
