import { DestroyRef, Directive, ElementRef, Renderer2, afterNextRender, inject, input, output } from '@angular/core';

/**
 * Which edges of the host element can be resized. Each edge maps to a CSS selector for the
 * drag handle that initiates the resize (the handle must be a descendant of the host element),
 * mirroring interact.js's `edges: { left: '.handle' }` configuration.
 */
export interface ResizableEdges {
    left?: string;
    right?: string;
    top?: string;
    bottom?: string;
}

/** Min/max size constraints in pixels. Any field may be omitted (treated as unconstrained). */
export interface ResizableConstraints {
    minWidth?: number;
    maxWidth?: number;
    minHeight?: number;
    maxHeight?: number;
}

/** Snapshot of the host element size (px) reported with resize events. */
export interface ResizableSizeEvent {
    width: number;
    height: number;
}

type ActiveEdge = keyof ResizableEdges;

/**
 * In-house replacement for interact.js `.resizable(...)`, implemented with Pointer Events
 * (so mouse, touch and pen are handled uniformly). Place it on the element to resize and point
 * each resizable edge at a handle selector:
 *
 * ```html
 * <div jhiResizable [resizableEdges]="{ left: '.draggable-left' }"
 *      [resizableConstraints]="{ minWidth: 215, maxWidth: 1500 }" (resizeEnd)="onResized($event)">
 *     <div class="draggable-left"></div>
 * </div>
 * ```
 *
 * A pointerdown on a configured handle starts a resize; the directive writes the clamped size to
 * the host's inline `width`/`height` during the drag (matching the previous interact.js behaviour)
 * and toggles the `card-resizable` class while resizing. Consumers that track the size in a signal
 * can read it from `(resizeMove)`/`(resizeEnd)` and set `[resizableApplyInlineSize]="false"` to own the
 * styling themselves. Handle lookup uses event delegation, so projected/late handles work too.
 */
@Directive({
    selector: '[jhiResizable]',
    host: {
        '(pointerdown)': 'onPointerDown($event)',
    },
})
export class ResizableDirective {
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly renderer = inject(Renderer2);
    private readonly destroyRef = inject(DestroyRef);

    /** Map of resizable edge -> handle selector (descendant of the host). */
    readonly resizableEdges = input<ResizableEdges>({});
    /** Min/max width/height in pixels. */
    readonly resizableConstraints = input<ResizableConstraints>({});
    /** When false, the handles are inert (used to toggle resizing on collapse). */
    readonly resizableEnabled = input<boolean>(true);
    /** When false, the directive emits sizes but does not write inline width/height itself. */
    readonly resizableApplyInlineSize = input<boolean>(true);

    readonly resizeStart = output<ResizableSizeEvent>();
    readonly resizeMove = output<ResizableSizeEvent>();
    readonly resizeEnd = output<ResizableSizeEvent>();

    private activeEdge?: ActiveEdge;
    private activePointerId?: number;
    private startX = 0;
    private startY = 0;
    private startWidth = 0;
    private startHeight = 0;
    private moveCleanup?: () => void;

    constructor() {
        // Keep `touch-action: none` on the handles so the browser does not hijack touch gestures.
        // Applied after the first render, when projected handles are present in the DOM.
        afterNextRender(() => this.applyHandleTouchAction(this.resizableEdges()));
        this.destroyRef.onDestroy(() => this.teardownActiveDrag());
    }

    private applyHandleTouchAction(edges: ResizableEdges): void {
        const hostEl = this.host.nativeElement;
        (Object.values(edges).filter(Boolean) as string[]).forEach((selector) => {
            hostEl.querySelectorAll<HTMLElement>(selector).forEach((handle) => this.renderer.setStyle(handle, 'touch-action', 'none'));
        });
    }

    /** Resolves which configured edge (if any) the pointerdown originated from, via event delegation. */
    private resolveEdge(target: EventTarget | null): ActiveEdge | undefined {
        if (!(target instanceof Element)) {
            return undefined;
        }
        const edges = this.resizableEdges();
        const hostEl = this.host.nativeElement;
        return (Object.keys(edges) as ActiveEdge[]).find((edge) => {
            const selector = edges[edge];
            if (!selector) {
                return false;
            }
            const handle = target.closest<HTMLElement>(selector);
            return !!handle && hostEl.contains(handle);
        });
    }

    protected onPointerDown(event: PointerEvent): void {
        // Ignore a second pointerdown while a resize is already in progress (prevents leaking
        // listeners and mixing pointer streams).
        if (this.activeEdge) {
            return;
        }
        if (!this.resizableEnabled()) {
            return;
        }
        if (event.pointerType === 'mouse' && event.button !== 0) {
            return;
        }
        const edge = this.resolveEdge(event.target);
        if (!edge) {
            return;
        }
        event.preventDefault();
        // Re-apply touch-action in case the handle was rendered after the initial afterNextRender pass.
        this.applyHandleTouchAction(this.resizableEdges());

        const rect = this.host.nativeElement.getBoundingClientRect();
        this.activeEdge = edge;
        this.activePointerId = event.pointerId;
        this.startX = event.clientX;
        this.startY = event.clientY;
        this.startWidth = rect.width;
        this.startHeight = rect.height;

        const hostEl = this.host.nativeElement;
        hostEl.setPointerCapture?.(event.pointerId);
        this.renderer.addClass(hostEl, 'card-resizable');

        const move = (e: PointerEvent) => this.onPointerMove(e);
        const up = (e: PointerEvent) => {
            if (e.pointerId !== this.activePointerId) {
                return;
            }
            hostEl.releasePointerCapture?.(e.pointerId);
            this.onPointerUp();
        };
        const unMove = this.renderer.listen(hostEl, 'pointermove', move);
        const unUp = this.renderer.listen(hostEl, 'pointerup', up);
        const unCancel = this.renderer.listen(hostEl, 'pointercancel', up);
        this.moveCleanup = () => {
            unMove();
            unUp();
            unCancel();
            this.moveCleanup = undefined;
        };

        this.resizeStart.emit({ width: this.startWidth, height: this.startHeight });
    }

    private onPointerMove(event: PointerEvent): void {
        if (!this.activeEdge || event.pointerId !== this.activePointerId) {
            return;
        }
        const { minWidth, maxWidth, minHeight, maxHeight } = this.resizableConstraints();
        let width = this.startWidth;
        let height = this.startHeight;

        switch (this.activeEdge) {
            case 'left':
                width = this.startWidth + (this.startX - event.clientX);
                break;
            case 'right':
                width = this.startWidth + (event.clientX - this.startX);
                break;
            case 'top':
                height = this.startHeight + (this.startY - event.clientY);
                break;
            case 'bottom':
                height = this.startHeight + (event.clientY - this.startY);
                break;
        }

        width = this.clamp(width, minWidth, maxWidth);
        height = this.clamp(height, minHeight, maxHeight);

        if (this.resizableApplyInlineSize()) {
            if (this.activeEdge === 'left' || this.activeEdge === 'right') {
                this.renderer.setStyle(this.host.nativeElement, 'width', `${width}px`);
            } else {
                this.renderer.setStyle(this.host.nativeElement, 'height', `${height}px`);
            }
        }
        this.resizeMove.emit({ width, height });
    }

    private onPointerUp(): void {
        if (!this.activeEdge) {
            return;
        }
        this.activeEdge = undefined;
        this.activePointerId = undefined;
        this.teardownActiveDrag();
        this.renderer.removeClass(this.host.nativeElement, 'card-resizable');
        const rect = this.host.nativeElement.getBoundingClientRect();
        this.resizeEnd.emit({ width: rect.width, height: rect.height });
    }

    private teardownActiveDrag(): void {
        this.moveCleanup?.();
    }

    private clamp(value: number, min?: number, max?: number): number {
        let result = value;
        if (min !== undefined) {
            result = Math.max(min, result);
        }
        if (max !== undefined) {
            result = Math.min(max, result);
        }
        return result;
    }
}
