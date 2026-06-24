import { DestroyRef, Directive, ElementRef, Renderer2, afterNextRender, effect, inject, input, output } from '@angular/core';
import { DOCUMENT } from '@angular/common';

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
    private readonly document = inject<Document>(DOCUMENT);

    /** Map of resizable edge -> handle selector (a descendant of the host, unless {@link resizableHandleOutsideHost}). */
    readonly resizableEdges = input<ResizableEdges>({});
    /** Min/max width/height in pixels. */
    readonly resizableConstraints = input<ResizableConstraints>({});
    /** When false, the handles are inert (used to toggle resizing on collapse). */
    readonly resizableEnabled = input<boolean>(true);
    /** When false, the directive emits sizes but does not write inline width/height itself. */
    readonly resizableApplyInlineSize = input<boolean>(true);
    /**
     * When true, the handle selector is also resolved against the host's sibling subtree (the host's parent),
     * so the drag handle can live next to the host instead of inside it - mirroring interact.js, whose handle
     * selector matched any element. This lets a divider sit between two flex columns (e.g. video | transcript)
     * rather than overlapping one of them. Defaults to false (handle must be a descendant), preserving the
     * behaviour of every existing consumer.
     */
    readonly resizableHandleOutsideHost = input<boolean>(false);

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
    private externalHandleCleanup?: () => void;

    constructor() {
        // Keep `touch-action: none` on the handles so the browser does not hijack touch gestures.
        // Applied after the first render, when projected handles are present in the DOM.
        afterNextRender(() => {
            this.applyHandleStyles(this.resizableEdges());
            this.attachExternalHandleListener();
        });
        // Re-apply the handle affordance styles (touch-action: none + resize cursor) whenever the edge map changes,
        // so handles whose edges are computed (e.g. modeling-assessment toggling horizontal/vertical resize) pick
        // up the styles as soon as they render, not only after the first pointerdown. afterNextRender covers the
        // initial pass; onPointerDown re-applies as a final safety net for handles re-created with an unchanged map.
        effect(() => {
            this.applyHandleStyles(this.resizableEdges());
        });
        this.destroyRef.onDestroy(() => {
            // Release a still-held pointer capture and reset state if the host is destroyed mid-drag.
            this.releaseCaptureSafely();
            this.activeEdge = undefined;
            this.activePointerId = undefined;
            this.teardownActiveDrag();
            this.renderer.removeStyle(this.document.body, 'user-select');
            this.externalHandleCleanup?.();
        });
    }

    /** Root to search for handle elements: the host's parent when handles live outside the host, else the host. */
    private handleSearchRoot(): HTMLElement | undefined {
        return this.resizableHandleOutsideHost() ? (this.host.nativeElement.parentElement ?? undefined) : this.host.nativeElement;
    }

    /**
     * When handles live outside the host, a pointerdown on the handle does not bubble through the host, so the
     * host `(pointerdown)` listener never sees it. Delegate from the host's parent instead; `onPointerDown` then
     * resolves the edge from the event target. No-op (and no double handling) when handles are inside the host.
     */
    private attachExternalHandleListener(): void {
        const parent = this.resizableHandleOutsideHost() ? this.host.nativeElement.parentElement : undefined;
        if (!parent) {
            return;
        }
        this.externalHandleCleanup?.();
        this.externalHandleCleanup = this.renderer.listen(parent, 'pointerdown', (event: PointerEvent) => this.onPointerDown(event));
    }

    /**
     * Prepares each handle: `touch-action: none` so the browser does not hijack touch gestures, and a resize
     * cursor so the handle visibly signals it is draggable (interact.js set this automatically; the testers
     * reported the affordance was missing on the directive-driven resizers). Left/right edges resize width
     * (col-resize), top/bottom edges resize height (row-resize).
     */
    private applyHandleStyles(edges: ResizableEdges): void {
        const root = this.handleSearchRoot();
        if (!root) {
            return;
        }
        (Object.entries(edges) as [ActiveEdge, string | undefined][]).forEach(([edge, selector]) => {
            if (!selector) {
                return;
            }
            const cursor = edge === 'left' || edge === 'right' ? 'col-resize' : 'row-resize';
            root.querySelectorAll<HTMLElement>(selector).forEach((handle) => {
                this.renderer.setStyle(handle, 'touch-action', 'none');
                this.renderer.setStyle(handle, 'cursor', cursor);
            });
        });
    }

    /** Resolves which configured edge (if any) the pointerdown originated from, via event delegation. */
    private resolveEdge(target: EventTarget | null): ActiveEdge | undefined {
        if (!(target instanceof Element)) {
            return undefined;
        }
        const edges = this.resizableEdges();
        const hostEl = this.host.nativeElement;
        const allowOutsideHost = this.resizableHandleOutsideHost();
        return (Object.keys(edges) as ActiveEdge[]).find((edge) => {
            const selector = edges[edge];
            if (!selector) {
                return false;
            }
            const handle = target.closest<HTMLElement>(selector);
            return !!handle && (allowOutsideHost || hostEl.contains(handle));
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
        // Re-apply handle styles in case the handle was rendered after the initial afterNextRender pass.
        this.applyHandleStyles(this.resizableEdges());

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
        // Suppress text selection for the whole drag; otherwise dragging across content selects text (the blue
        // selection flicker testers reported). Restored in onPointerUp / teardown.
        this.renderer.setStyle(this.document.body, 'user-select', 'none');

        const move = (e: PointerEvent) => this.onPointerMove(e);
        // Same handler for pointerup and pointercancel. Releasing the capture is done inside onPointerUp via
        // releaseCaptureSafely(); we must NOT call releasePointerCapture() here unconditionally, because on
        // pointercancel the browser has already released the capture and the call would throw InvalidPointerId,
        // aborting the teardown and leaving the directive wedged (stuck card-resizable + leaked listeners).
        const up = (e: PointerEvent) => {
            if (e.pointerId !== this.activePointerId) {
                return;
            }
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
        this.releaseCaptureSafely();
        this.activeEdge = undefined;
        this.activePointerId = undefined;
        this.teardownActiveDrag();
        this.renderer.removeClass(this.host.nativeElement, 'card-resizable');
        this.renderer.removeStyle(this.document.body, 'user-select');
        const rect = this.host.nativeElement.getBoundingClientRect();
        this.resizeEnd.emit({ width: rect.width, height: rect.height });
    }

    /**
     * Releases the active pointer capture if (and only if) the host still holds it. On `pointercancel` the
     * browser has already released it, so calling releasePointerCapture() would throw `InvalidPointerId`; the
     * hasPointerCapture guard and the try/catch make this safe to call from pointerup, pointercancel and destroy.
     */
    private releaseCaptureSafely(): void {
        const pointerId = this.activePointerId;
        if (pointerId === undefined) {
            return;
        }
        const hostEl = this.host.nativeElement;
        try {
            if (hostEl.hasPointerCapture?.(pointerId)) {
                hostEl.releasePointerCapture(pointerId);
            }
        } catch {
            // The capture was already released (e.g. on pointercancel); nothing to do.
        }
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
