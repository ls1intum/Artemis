import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, TemplateRef, computed, contentChildren, effect, inject, input, output, signal } from '@angular/core';
import { TUM_AET_UI_TRANSLATOR } from '../i18n/tumaet-ui-translations';

/** Two resizable panels. Arrow keys resize by 2%, Shift by 10%, and Home/End select the limits. */
@Component({
    selector: 'tumaet-ui-splitter',
    templateUrl: './tumaet-ui-splitter.component.html',
    styleUrl: './tumaet-ui-splitter.component.scss',
    imports: [NgTemplateOutlet],
    host: { '[attr.data-layout]': 'layout()' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiSplitterComponent {
    private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly translator = inject(TUM_AET_UI_TRANSLATOR);

    readonly layout = input<'horizontal' | 'vertical'>('horizontal');
    /** Initial percentage sizes; subsequent user resizes are retained until these inputs change. */
    readonly panelSizes = input<readonly number[]>([50, 50]);
    readonly minSizes = input<readonly number[]>([10, 10]);
    /** Optional local-storage key. Each orientation retains an independent size. */
    readonly stateKey = input<string>();
    readonly ariaLabel = input<string>();
    /** Emits both percentage sizes after pointer or keyboard resizing completes. */
    readonly resizeEnd = output<number[]>();

    protected readonly panels = contentChildren('panel', { read: TemplateRef<unknown> });
    protected readonly size = signal(50);
    protected readonly minimum = computed(() => Math.min(100, Math.max(0, this.minSizes()[0] ?? 0)));
    protected readonly maximum = computed(() => Math.max(this.minimum(), 100 - Math.max(0, this.minSizes()[1] ?? 0)));
    protected readonly separatorLabel = computed(() => {
        this.translator.translationChanges?.();
        return this.ariaLabel() || this.translator.translate('tumAetUi.splitter.resize');
    });
    private pointerId?: number;

    constructor() {
        effect(() => {
            const key = this.storageKey();
            let initial = this.panelSizes()[0] ?? 50;
            if (key) {
                try {
                    const stored = localStorage.getItem(key);
                    if (stored !== null && Number.isFinite(Number(stored))) {
                        initial = Number(stored);
                    }
                } catch {
                    // Resizing remains available when browser storage is disabled.
                }
            }
            this.size.set(this.clamp(initial));
        });
    }

    protected startResize(event: PointerEvent): void {
        if (event.button !== 0) {
            return;
        }
        event.preventDefault();
        this.pointerId = event.pointerId;
        (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    }

    protected moveResize(event: PointerEvent): void {
        if (event.pointerId !== this.pointerId) {
            return;
        }
        const bounds = this.element.nativeElement.getBoundingClientRect();
        const vertical = this.layout() === 'vertical';
        const dimension = vertical ? bounds.height : bounds.width;
        if (dimension > 0) {
            const offset = vertical ? event.clientY - bounds.top : event.clientX - bounds.left;
            this.size.set(this.clamp((offset / dimension) * 100));
        }
    }

    protected finishResize(event: PointerEvent): void {
        if (event.pointerId === this.pointerId) {
            this.pointerId = undefined;
            this.persist();
        }
    }

    protected resizeWithKeyboard(event: KeyboardEvent): void {
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
        this.size.set(this.clamp(value));
        this.persist();
    }

    private clamp(value: number): number {
        return Math.max(this.minimum(), Math.min(this.maximum(), Number.isFinite(value) ? value : 50));
    }

    private storageKey(): string | undefined {
        const key = this.stateKey();
        return key ? `${key}-${this.layout()}` : undefined;
    }

    private persist(): void {
        const key = this.storageKey();
        if (key) {
            try {
                localStorage.setItem(key, String(this.size()));
            } catch {
                // Storage failures must not interrupt the active workflow.
            }
        }
        this.resizeEnd.emit([this.size(), 100 - this.size()]);
    }
}
