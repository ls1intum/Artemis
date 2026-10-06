import { DestroyRef, Directive, ElementRef, Renderer2, afterNextRender, effect, inject, input, output } from '@angular/core';
import { DOCUMENT } from '@angular/common';

/**
 * Which edges of the host can be resized. Each edge maps to a CSS selector for the
 * drag handle that starts the resize — the handle is looked up inside the host, or
 * in its parent when `resizableHandleOutsideHost` is set.
 */
export interface ResizableEdges {
    left?: string;
    right?: string;
    top?: string;
    bottom?: string;
}

/** Min/max size in pixels. Any field may be omitted, meaning unconstrained on that side. */
export interface ResizableConstraints {
    minWidth?: number;
    maxWidth?: number;
    minHeight?: number;
    maxHeight?: number;
}

/** Snapshot of the host's size in pixels, reported with every resize event. */
export interface ResizableSizeEvent {
    width: number;
    height: number;
}

type ActiveEdge = keyof ResizableEdges;
const HANDLE_ATTRIBUTES = ['role', 'tabindex', 'aria-disabled', 'aria-orientation', 'aria-controls', 'aria-valuenow', 'aria-valuemin', 'aria-valuemax'] as const;

/**
 * Whether this handle should be described to assistive technology as a separator.
 *
 * An element that is already interactive — a `<button>` that also resets the split, say — carries its own role and
 * keyboard contract, and overriding those would rename the control and silence its own keys. Such a handle still
 * drags; it is simply not re-labelled. A handle with no accessible name is skipped for the opposite reason: a
 * separator announced with no name is worse than an undecorated div.
 */
function isSeparatorHandle(handle: HTMLElement): boolean {
    const hasAccessibleName = handle.hasAttribute('aria-label') || handle.hasAttribute('aria-labelledby');
    const isNativelyInteractive = handle.matches('button, a[href], input, select, textarea, [role]:not([role="separator"])');
    return hasAccessibleName && !isNativelyInteractive;
}

interface HandleState {
    cursor: string;
    touchAction: string;
    attributes: Map<(typeof HANDLE_ATTRIBUTES)[number], string | null>;
}

let nextResizableId = 0;

/**
 * Resizes the host by dragging selector-matched handles. Built on Pointer Events, so
 * mouse, touch and pen are handled uniformly.
 *
 * ```html
 * <div jhiResizable [resizableEdges]="{ left: '.draggable-left' }" [resizableConstraints]="{ minWidth: 215, maxWidth: 1500 }" (resizeEnd)="onResized($event)">
 *     <div class="draggable-left"></div>
 * </div>
 * ```
 *
 * A pointerdown on a configured handle starts a resize, and the clamped size is written
 * to the host's inline `width`/`height` during the drag. A consumer that owns its own
 * sizing — one driving a signal, say — sets `resizableApplyInlineSize` to false and
 * applies the reported size itself.
 *
 * A handle carrying an accessible name is also promoted to a keyboard-operable
 * `role="separator"`, so the same affordance works with arrow keys, Home and End.
 */
@Directive({
    selector: '[jhiResizable]',
    host: {
        '(pointerdown)': 'onPointerDown($event)',
        '(keydown)': 'onKeyDown($event)',
    },
})
export class ResizableDirective {
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly renderer = inject(Renderer2);
    private readonly destroyRef = inject(DestroyRef);
    private readonly document = inject<Document>(DOCUMENT);

    readonly resizableEdges = input<ResizableEdges>({});
    readonly resizableConstraints = input<ResizableConstraints>({});
    readonly resizableEnabled = input<boolean>(true);
    readonly resizableApplyInlineSize = input<boolean>(true);
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
    private handleMutationObserver?: MutationObserver;
    private handleResizeObserver?: ResizeObserver;
    private handleStylesFrame?: number;
    private readonly managedHandles = new Map<HTMLElement, HandleState>();

    constructor() {
        afterNextRender(() => {
            this.applyHandleStyles(this.resizableEdges());
            this.attachExternalHandleListener();
            this.observeHandleChanges();
        });
        effect(() => {
            this.attachExternalHandleListener();
            this.applyHandleStyles(this.resizableEdges());
            this.observeHandleChanges();
        });
        this.destroyRef.onDestroy(() => {
            this.releaseCaptureSafely();
            this.activeEdge = undefined;
            this.activePointerId = undefined;
            this.teardownActiveDrag();
            this.renderer.removeStyle(this.document.body, 'user-select');
            this.externalHandleCleanup?.();
            this.handleMutationObserver?.disconnect();
            this.handleResizeObserver?.disconnect();
            if (this.handleStylesFrame !== undefined) {
                window.cancelAnimationFrame(this.handleStylesFrame);
                this.handleStylesFrame = undefined;
            }
            this.restoreManagedHandles();
        });
    }

    private handleSearchRoot(): HTMLElement | undefined {
        return this.resizableHandleOutsideHost() ? (this.host.nativeElement.parentElement ?? undefined) : this.host.nativeElement;
    }

    private attachExternalHandleListener(): void {
        this.externalHandleCleanup?.();
        const parent = this.resizableHandleOutsideHost() ? this.host.nativeElement.parentElement : undefined;
        if (!parent) {
            return;
        }
        const removePointerListener = this.renderer.listen(parent, 'pointerdown', (event: PointerEvent) => this.onPointerDown(event));
        const removeKeyboardListener = this.renderer.listen(parent, 'keydown', (event: KeyboardEvent) => this.onKeyDown(event));
        this.externalHandleCleanup = () => {
            removePointerListener();
            removeKeyboardListener();
            this.externalHandleCleanup = undefined;
        };
    }

    private observeHandleChanges(): void {
        this.handleMutationObserver?.disconnect();
        this.handleResizeObserver?.disconnect();
        const root = this.handleSearchRoot();
        if (!root || !Object.values(this.resizableEdges()).some(Boolean)) {
            this.handleMutationObserver = undefined;
            return;
        }
        this.handleMutationObserver = new MutationObserver(() => this.scheduleHandleStyles());
        this.handleMutationObserver.observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-label', 'aria-labelledby'] });
        this.handleResizeObserver = new ResizeObserver(() => this.scheduleHandleStyles());
        this.handleResizeObserver.observe(this.host.nativeElement);
    }

    /**
     * Coalesces handle upkeep into one frame. The observer watches `childList` + `subtree`, so a host whose content
     * churns mutates it on every keystroke, and the aria upkeep measures the host — reacting per mutation would force
     * a reflow per keystroke.
     */
    private scheduleHandleStyles(): void {
        if (this.handleStylesFrame !== undefined) {
            return;
        }
        this.handleStylesFrame = window.requestAnimationFrame(() => {
            this.handleStylesFrame = undefined;
            this.applyHandleStyles(this.resizableEdges());
        });
    }

    private setStyleIfChanged(element: HTMLElement, property: string, value: string): void {
        if (element.style.getPropertyValue(property) !== value) {
            this.renderer.setStyle(element, property, value);
        }
    }

    private setAttributeIfChanged(element: HTMLElement, attribute: string, value: string): void {
        if (element.getAttribute(attribute) !== value) {
            this.renderer.setAttribute(element, attribute, value);
        }
    }

    private removeAttributeIfPresent(element: HTMLElement, attribute: string): void {
        if (element.hasAttribute(attribute)) {
            this.renderer.removeAttribute(element, attribute);
        }
    }

    private applyHandleStyles(edges: ResizableEdges): void {
        const root = this.handleSearchRoot();
        if (!root) {
            this.restoreManagedHandles();
            return;
        }
        const activeHandles = new Map<HTMLElement, ActiveEdge>();
        (Object.entries(edges) as [ActiveEdge, string | undefined][]).forEach(([edge, selector]) => {
            if (!selector) {
                return;
            }
            root.querySelectorAll<HTMLElement>(selector).forEach((handle) => activeHandles.set(handle, edge));
        });

        for (const handle of this.managedHandles.keys()) {
            if (!activeHandles.has(handle)) {
                this.restoreHandle(handle);
            }
        }

        activeHandles.forEach((edge, handle) => {
            this.captureHandleState(handle);
            const cursor = edge === 'left' || edge === 'right' ? 'col-resize' : 'row-resize';
            const enabled = this.resizableEnabled();
            this.setStyleIfChanged(handle, 'touch-action', enabled ? 'none' : 'auto');
            this.setStyleIfChanged(handle, 'cursor', enabled ? cursor : 'default');
            if (!isSeparatorHandle(handle)) {
                this.restoreHandleAttributes(handle);
                return;
            }
            this.setAttributeIfChanged(handle, 'role', 'separator');
            this.setAttributeIfChanged(handle, 'tabindex', enabled ? '0' : '-1');
            if (enabled) {
                this.removeAttributeIfPresent(handle, 'aria-disabled');
            } else {
                this.setAttributeIfChanged(handle, 'aria-disabled', 'true');
            }
            this.setAttributeIfChanged(handle, 'aria-orientation', edge === 'left' || edge === 'right' ? 'vertical' : 'horizontal');
            this.setAttributeIfChanged(handle, 'aria-controls', this.ensureHostId());
            this.updateHandleAria(handle, edge);
        });
    }

    private captureHandleState(handle: HTMLElement): void {
        if (this.managedHandles.has(handle)) {
            return;
        }
        this.managedHandles.set(handle, {
            cursor: handle.style.cursor,
            touchAction: handle.style.touchAction,
            attributes: new Map(HANDLE_ATTRIBUTES.map((attribute) => [attribute, handle.getAttribute(attribute)])),
        });
    }

    private restoreManagedHandles(): void {
        for (const handle of this.managedHandles.keys()) {
            this.restoreHandle(handle);
        }
    }

    private restoreHandle(handle: HTMLElement): void {
        const state = this.managedHandles.get(handle);
        if (!state) {
            return;
        }
        this.restoreStyle(handle, 'cursor', state.cursor);
        this.restoreStyle(handle, 'touch-action', state.touchAction);
        this.restoreHandleAttributes(handle);
        this.managedHandles.delete(handle);
    }

    private restoreHandleAttributes(handle: HTMLElement): void {
        const attributes = this.managedHandles.get(handle)?.attributes;
        if (!attributes) {
            return;
        }
        attributes.forEach((value, attribute) => {
            if (value === null) {
                this.renderer.removeAttribute(handle, attribute);
            } else {
                this.renderer.setAttribute(handle, attribute, value);
            }
        });
    }

    private restoreStyle(handle: HTMLElement, property: string, value: string): void {
        if (value) {
            this.renderer.setStyle(handle, property, value);
        } else {
            this.renderer.removeStyle(handle, property);
        }
    }

    private ensureHostId(): string {
        const host = this.host.nativeElement;
        if (!host.id) {
            this.renderer.setAttribute(host, 'id', `resizable-region-${++nextResizableId}`);
        }
        return host.id;
    }

    private updateHandleAria(handle: HTMLElement, edge: ActiveEdge, size?: ResizableSizeEvent): void {
        const horizontal = edge === 'left' || edge === 'right';
        const constraints = this.resizableConstraints();
        // One measurement, not one per axis: `getBoundingClientRect` forces layout.
        const hostRect = size ? undefined : this.host.nativeElement.getBoundingClientRect();
        const current = size ?? { width: hostRect!.width, height: hostRect!.height };
        const value = Math.round(horizontal ? current.width : current.height);
        const min = horizontal ? constraints.minWidth : constraints.minHeight;
        const max = horizontal ? constraints.maxWidth : constraints.maxHeight;
        this.setAttributeIfChanged(handle, 'aria-valuenow', `${value}`);
        if (min !== undefined) {
            this.setAttributeIfChanged(handle, 'aria-valuemin', `${min}`);
        } else {
            this.removeAttributeIfPresent(handle, 'aria-valuemin');
        }
        if (max !== undefined) {
            this.setAttributeIfChanged(handle, 'aria-valuemax', `${max}`);
        } else {
            this.removeAttributeIfPresent(handle, 'aria-valuemax');
        }
    }

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
        this.renderer.setStyle(this.document.body, 'user-select', 'none');

        const move = (e: PointerEvent) => this.onPointerMove(e);
        const up = (e: PointerEvent) => {
            if (e.pointerId !== this.activePointerId) {
                return;
            }
            this.onPointerUp();
        };
        const unMove = this.renderer.listen(hostEl, 'pointermove', move);
        const unUp = this.renderer.listen(hostEl, 'pointerup', up);
        const unCancel = this.renderer.listen(hostEl, 'pointercancel', up);
        const unLostCapture = this.renderer.listen(hostEl, 'lostpointercapture', (e: PointerEvent) => {
            if (e.pointerId === this.activePointerId) {
                this.finishResize();
            }
        });
        this.moveCleanup = () => {
            unMove();
            unUp();
            unCancel();
            unLostCapture();
            this.moveCleanup = undefined;
        };

        this.resizeStart.emit({ width: this.startWidth, height: this.startHeight });
    }

    protected onKeyDown(event: KeyboardEvent): void {
        if (!this.resizableEnabled()) {
            return;
        }
        const edge = this.resolveEdge(event.target);
        if (!edge) {
            return;
        }

        const horizontal = edge === 'left' || edge === 'right';
        const decrementKey = horizontal ? 'ArrowLeft' : 'ArrowUp';
        const incrementKey = horizontal ? 'ArrowRight' : 'ArrowDown';
        const constraints = this.resizableConstraints();
        const min = horizontal ? constraints.minWidth : constraints.minHeight;
        const max = horizontal ? constraints.maxWidth : constraints.maxHeight;
        const rect = this.host.nativeElement.getBoundingClientRect();
        const current = horizontal ? rect.width : rect.height;
        let next: number | undefined;

        if (event.key === 'Home' && min !== undefined) {
            next = min;
        } else if (event.key === 'End' && max !== undefined) {
            next = max;
        } else if (event.key === decrementKey || event.key === incrementKey) {
            const direction = event.key === incrementKey ? 1 : -1;
            const edgeDirection = edge === 'left' || edge === 'top' ? -1 : 1;
            next = current + direction * edgeDirection * 16;
        }

        if (next === undefined) {
            return;
        }

        event.preventDefault();
        const width = horizontal ? this.clamp(next, constraints.minWidth, constraints.maxWidth) : rect.width;
        const height = horizontal ? rect.height : this.clamp(next, constraints.minHeight, constraints.maxHeight);
        const size = { width, height };
        this.resizeStart.emit({ width: rect.width, height: rect.height });
        if (this.resizableApplyInlineSize()) {
            this.renderer.setStyle(this.host.nativeElement, horizontal ? 'width' : 'height', `${horizontal ? width : height}px`);
        }
        const doubleClickHandle = event.target as HTMLElement;
        if (isSeparatorHandle(doubleClickHandle)) {
            this.updateHandleAria(doubleClickHandle, edge, size);
        }
        this.resizeMove.emit(size);
        this.resizeEnd.emit(size);
    }

    private onPointerMove(event: PointerEvent): void {
        if (!this.activeEdge || event.pointerId !== this.activePointerId) {
            return;
        }
        if (!this.resizableEnabled()) {
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
        const handle = this.handleSearchRoot()?.querySelector<HTMLElement>(this.resizableEdges()[this.activeEdge]!);
        if (handle && isSeparatorHandle(handle)) {
            this.updateHandleAria(handle, this.activeEdge, { width, height });
        }
        this.resizeMove.emit({ width, height });
    }

    private onPointerUp(): void {
        if (!this.activeEdge) {
            return;
        }
        const pointerId = this.activePointerId;
        this.activeEdge = undefined;
        this.activePointerId = undefined;
        this.releaseCaptureSafely(pointerId);
        this.finishResize();
    }

    private finishResize(): void {
        this.activeEdge = undefined;
        this.activePointerId = undefined;
        this.teardownActiveDrag();
        this.renderer.removeClass(this.host.nativeElement, 'card-resizable');
        this.renderer.removeStyle(this.document.body, 'user-select');
        const rect = this.host.nativeElement.getBoundingClientRect();
        this.resizeEnd.emit({ width: rect.width, height: rect.height });
    }

    private releaseCaptureSafely(pointerId = this.activePointerId): void {
        if (pointerId === undefined) {
            return;
        }
        const hostEl = this.host.nativeElement;
        try {
            if (hostEl.hasPointerCapture?.(pointerId)) {
                hostEl.releasePointerCapture(pointerId);
            }
        } catch (error) {
            if (!(error instanceof DOMException && error.name === 'NotFoundError')) {
                throw error;
            }
        }
    }

    private teardownActiveDrag(): void {
        this.moveCleanup?.();
    }

    private clamp(value: number, min?: number, max?: number): number {
        let result = value;
        if (max !== undefined) {
            result = Math.min(max, result);
        }
        if (min !== undefined) {
            result = Math.max(min, result);
        }
        return result;
    }
}
