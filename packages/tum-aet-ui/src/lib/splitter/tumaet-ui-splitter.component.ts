import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    Renderer2,
    TemplateRef,
    computed,
    contentChildren,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
} from '@angular/core';
import { TUM_AET_UI_TRANSLATOR } from '../i18n/tumaet-ui-translations';

/**
 * Two persistent panels separated by a keyboard and pointer operated separator.
 * Sizes and minimums are percentages of the available space excluding the gutter.
 * Arrow keys adjust by two percentage points (ten with Shift); Home/End reach the limits.
 */
@Component({
    selector: 'tumaet-ui-splitter',
    imports: [NgTemplateOutlet],
    templateUrl: './tumaet-ui-splitter.component.html',
    styleUrl: './tumaet-ui-splitter.component.scss',
    host: { '[attr.data-layout]': 'layout()', '[class.vertical]': 'layout() === "vertical"' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiSplitterComponent {
    private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly document = inject(DOCUMENT);
    private readonly destroyRef = inject(DestroyRef);
    private readonly renderer = inject(Renderer2);
    private readonly translator = inject(TUM_AET_UI_TRANSLATOR);

    readonly layout = input<'horizontal' | 'vertical'>('horizontal');
    /** Initial nonnegative relative weights, normalized to 100; user resizes are retained until these inputs change. */
    readonly panelSizes = input<readonly number[]>([50, 50]);
    /** Impossible minimums whose sum exceeds 100 are proportionally normalized. */
    readonly minSizes = input<readonly number[]>([10, 10]);
    /** Optional local-storage key. Each orientation retains an independent size. */
    readonly stateKey = input<string>();
    readonly ariaLabel = input<string>();
    /** Explicit gutter width in pixels; otherwise the splitter divider CSS token applies. */
    readonly gutterSize = input<number>();
    /** Fix the second panel to a CSS size and hide the separator while retaining both panel views. */
    readonly collapsedSecondSize = input<string>();
    readonly sizesChange = output<number[]>();
    readonly resizeEnd = output<number[]>();

    protected readonly panels = contentChildren('panel', { read: TemplateRef<unknown> });
    protected readonly size = signal(50);
    protected readonly minimums = computed(() => {
        const first = this.percentage(this.minSizes()[0]);
        const second = this.percentage(this.minSizes()[1]);
        const sum = first + second;
        return sum > 100 ? [(first * 100) / sum, (second * 100) / sum] : [first, second];
    });
    protected readonly minimum = computed(() => this.minimums()[0]);
    protected readonly maximum = computed(() => 100 - this.minimums()[1]);
    protected readonly safeGutterSize = computed(() => {
        const size = this.gutterSize();
        return size !== undefined && Number.isFinite(size) ? Math.max(0, size) : undefined;
    });
    protected readonly separatorLabel = computed(() => {
        this.translator.translationChanges?.();
        return this.ariaLabel() || this.translator.translate('tumAetUi.splitter.resize');
    });
    private dragCleanup?: () => void;

    constructor() {
        this.destroyRef.onDestroy(() => this.dragCleanup?.());
        effect(() => {
            const key = this.storageKey();
            const [first, second] = this.panelSizes();
            const sum = first + second;
            let initial = Number.isFinite(sum) && first >= 0 && second >= 0 && sum > 0 ? (first * 100) / sum : 50;
            if (key) {
                try {
                    const stored = this.document.defaultView?.localStorage.getItem(key);
                    if (stored !== undefined && stored !== null && Number.isFinite(Number(stored))) {
                        initial = Number(stored);
                    }
                } catch {
                    // Resizing remains available when browser storage is disabled.
                }
            }
            const next = this.clamp(initial);
            // Normalizing a parent's sizesChange echo can introduce insignificant rounding.
            if (Math.abs(next - untracked(this.size)) > 1e-9) {
                this.dragCleanup?.();
            }
            this.size.set(next);
        });
        effect(() => {
            this.minSizes();
            this.layout();
            this.collapsedSecondSize();
            this.gutterSize();
            this.dragCleanup?.();
        });
    }

    protected resizeWithKeyboard(event: KeyboardEvent): void {
        if (this.collapsedSecondSize()) {
            return;
        }
        const decrease = this.layout() === 'horizontal' ? 'ArrowLeft' : 'ArrowUp';
        const increase = this.layout() === 'horizontal' ? 'ArrowRight' : 'ArrowDown';
        let value = this.size();
        if (event.key === decrease) {
            value -= event.shiftKey ? 10 : 2;
        } else if (event.key === increase) {
            value += event.shiftKey ? 10 : 2;
        } else if (event.key === 'Home') {
            value = this.minimum();
        } else if (event.key === 'End') {
            value = this.maximum();
        } else {
            return;
        }
        event.preventDefault();
        this.update(value);
        this.persist();
    }

    protected startResize(event: PointerEvent): void {
        if (event.button !== 0 || this.collapsedSecondSize()) {
            return;
        }
        event.preventDefault();
        this.dragCleanup?.();
        const horizontal = this.layout() === 'horizontal';
        const bounds = this.element.nativeElement.getBoundingClientRect();
        const separator = event.currentTarget as HTMLElement;
        const gutterBounds = separator.getBoundingClientRect();
        const measuredGutter = horizontal ? gutterBounds.width : gutterBounds.height;
        const cssGutter = Number.parseFloat(this.document.defaultView?.getComputedStyle(separator).flexBasis ?? '');
        const fallbackGutter = this.safeGutterSize() ?? (Number.isFinite(cssGutter) && cssGutter >= 0 ? cssGutter : 6);
        const available = (horizontal ? bounds.width : bounds.height) - (measuredGutter > 0 ? measuredGutter : fallbackGutter);
        if (available <= 0) {
            return;
        }
        const start = horizontal ? event.clientX : event.clientY;
        const previous = this.size();
        const pointerId = event.pointerId;
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
        const cancelPointer = (cancelled: PointerEvent) => {
            if (cancelled.pointerId === pointerId) {
                cancel();
            }
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
                    this.persist();
                }
            }),
        );
        listeners.push(this.renderer.listen(this.document, 'pointercancel', cancelPointer));
        listeners.push(this.renderer.listen(separator, 'lostpointercapture', cancelPointer));
        if (this.document.defaultView) {
            listeners.push(this.renderer.listen(this.document.defaultView, 'blur', cancel));
        }
    }

    private percentage(value: number | undefined): number {
        return Number.isFinite(value) ? Math.max(0, Math.min(100, value!)) : 0;
    }

    private clamp(value: number): number {
        return Math.max(this.minimum(), Math.min(this.maximum(), Number.isFinite(value) ? value : 50));
    }

    private values(): number[] {
        return [this.size(), 100 - this.size()];
    }

    private update(value: number): void {
        this.size.set(this.clamp(value));
        this.sizesChange.emit(this.values());
    }

    private storageKey(): string | undefined {
        const key = this.stateKey();
        return key ? `${key}-${this.layout()}` : undefined;
    }

    private persist(): void {
        const key = this.storageKey();
        if (key) {
            try {
                this.document.defaultView?.localStorage.setItem(key, String(this.size()));
            } catch {
                // Storage failures must not interrupt the active workflow.
            }
        }
        this.resizeEnd.emit(this.values());
    }
}
