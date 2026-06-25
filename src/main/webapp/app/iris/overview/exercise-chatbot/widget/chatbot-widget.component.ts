import { BreakpointObserver } from '@angular/cdk/layout';
import { AfterViewInit, ChangeDetectionStrategy, Component, HostListener, OnDestroy, Renderer2, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DOCUMENT } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { IrisBaseChatbotComponent } from '../../base-chatbot/iris-base-chatbot.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisMessageContextDTO } from 'app/iris/shared/entities/iris-message-context-dto.model';
import { getIsMobileSignal } from 'app/foundation/util/global.utils';

export interface ChatbotWidgetData {
    contextProvider?: (() => IrisMessageContextDTO[]) | undefined;
}

@Component({
    selector: 'jhi-chatbot-widget',
    templateUrl: './chatbot-widget.component.html',
    styleUrls: ['./chatbot-widget.component.scss'],
    imports: [IrisBaseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisChatbotWidgetComponent implements OnDestroy, AfterViewInit {
    private breakpointObserver = inject(BreakpointObserver);
    private document = inject<Document>(DOCUMENT);
    private router = inject(Router);
    private dialog = inject(MatDialog);
    private chatService = inject(IrisChatService);
    protected readonly data = inject<ChatbotWidgetData>(MAT_DIALOG_DATA, { optional: true });
    private renderer = inject(Renderer2);

    readonly isMobile = getIsMobileSignal(this.breakpointObserver);

    // User preferences (constants)
    readonly initialWidth = 450;
    readonly initialHeight = 600;
    readonly fullWidthFactor = 0.93;
    readonly fullHeightFactor = 0.85;
    readonly fullSize = signal(false);
    public ButtonType = ButtonType;

    /** Distance (px) from a border within which a pointerdown starts an edge resize. */
    private static readonly EDGE_MARGIN = 10;
    /**
     * Selector for controls whose clicks must never be hijacked by drag/resize gestures. Besides the obvious form
     * controls it covers: the session-switcher menu and its wrapper (`.p-menu` / `[role="menu"]` /
     * `.session-switcher-inline-menu`) so a press on the menu chrome or its wrapper gutter does not drag the widget
     * out from under the open menu; and the PrimeNG context select (`.p-select` / `[role="combobox"]`), whose label
     * area is not a `<select>`/`[role="button"]`, so its open-dropdown click is preserved regardless of layout.
     */
    private static readonly INTERACTIVE_CONTROL_SELECTOR =
        'button, a, input, textarea, select, [role="button"], [role="menuitem"], [contenteditable="true"], .p-menu, [role="menu"], .session-switcher-inline-menu, .p-select, [role="combobox"]';
    private widgetEl?: HTMLElement;
    private pointerDownCleanup?: () => void;
    private hoverMoveCleanup?: () => void;
    private hoverLeaveCleanup?: () => void;
    private gestureCleanup?: () => void;
    private gesture?: 'drag' | 'resize';
    private activePointerId?: number;
    private resizeEdges = { left: false, right: false, top: false, bottom: false };
    private startPointerX = 0;
    private startPointerY = 0;
    private startWidth = 0;
    private startHeight = 0;
    private startTranslateX = 0;
    private startTranslateY = 0;

    constructor() {
        this.router.events
            .pipe(
                filter((event) => event instanceof NavigationStart),
                takeUntilDestroyed(),
            )
            .subscribe(() => this.dialog.closeAll());
    }

    @HostListener('window:resize')
    onResize() {
        this.setPositionAndScale();
    }

    ngAfterViewInit() {
        // In-house Pointer-Events drag + resize (replaces interact.js). Drag from `.chat-header`,
        // resize from the left/right/bottom borders and the `.chat-widget-top-resize-area`; the
        // widget is kept inside the `.cdk-overlay-container` and never shrinks below its initial size.
        const widget = this.document.querySelector<HTMLElement>('.chat-widget') ?? undefined;
        this.widgetEl = widget;
        if (widget) {
            this.pointerDownCleanup = this.renderer.listen(widget, 'pointerdown', (event: PointerEvent) => this.onPointerDown(event));
            // Hover affordance: mirror interact.js, which changed the cursor to a resize arrow near a resizable
            // edge so users can tell the widget is resizable. Updated on every hover move and cleared on leave.
            this.hoverMoveCleanup = this.renderer.listen(widget, 'pointermove', (event: PointerEvent) => this.onHoverMove(event));
            this.hoverLeaveCleanup = this.renderer.listen(widget, 'pointerleave', () => this.setWidgetCursor(''));
        }
        this.setPositionAndScale();
    }

    /**
     * Computes which borders of the widget the pointer is on, within {@link EDGE_MARGIN}. The top edge is the
     * dedicated `.chat-widget-top-resize-area` strip (the header sits just below it and is the drag surface).
     */
    private detectResizeEdges(event: PointerEvent, rect: DOMRect, target: Element | null) {
        const margin = IrisChatbotWidgetComponent.EDGE_MARGIN;
        return {
            left: event.clientX - rect.left <= margin,
            right: rect.right - event.clientX <= margin,
            bottom: rect.bottom - event.clientY <= margin,
            top: !!target?.closest('.chat-widget-top-resize-area'),
        };
    }

    /** Maps a set of active edges to the matching resize cursor (diagonal at corners), or '' for none. */
    private resizeCursor(edges: { left: boolean; right: boolean; top: boolean; bottom: boolean }): string {
        const { left, right, top, bottom } = edges;
        if ((top && left) || (bottom && right)) {
            return 'nwse-resize';
        }
        if ((top && right) || (bottom && left)) {
            return 'nesw-resize';
        }
        if (left || right) {
            return 'ew-resize';
        }
        if (top || bottom) {
            return 'ns-resize';
        }
        return '';
    }

    /**
     * Updates the cursor while hovering (no active gesture) so the resize affordance is visible: a resize arrow
     * near a border, otherwise the inline cursor is cleared so the CSS `grab` cursor on the header (or the default)
     * applies. Skipped for touch/pen and over interactive controls, which keep their own cursor.
     */
    private onHoverMove(event: PointerEvent): void {
        const widget = this.widgetEl;
        if (!widget || this.gesture || event.pointerType !== 'mouse') {
            return;
        }
        const target = event.target as Element | null;
        if (target?.closest(IrisChatbotWidgetComponent.INTERACTIVE_CONTROL_SELECTOR)) {
            this.setWidgetCursor('');
            return;
        }
        const cursor = this.resizeCursor(this.detectResizeEdges(event, widget.getBoundingClientRect(), target));
        this.setWidgetCursor(cursor);
    }

    /** Sets (or clears, when empty) the inline cursor on the widget. */
    private setWidgetCursor(cursor: string): void {
        const widget = this.widgetEl;
        if (!widget) {
            return;
        }
        if (cursor) {
            this.renderer.setStyle(widget, 'cursor', cursor);
        } else {
            this.renderer.removeStyle(widget, 'cursor');
        }
    }

    private onPointerDown(event: PointerEvent): void {
        const widget = this.widgetEl;
        // Ignore overlapping gesture starts (a second pointer down while one is active).
        if (!widget || this.gesture || (event.pointerType === 'mouse' && event.button !== 0)) {
            return;
        }
        const target = event.target as Element | null;
        // Never hijack a pointerdown on an interactive control (header buttons: info, new chat, fullscreen, close,
        // the session switcher; plus the message input, send button and the source dropdown). interact.js preserved
        // these clicks via its drag threshold; the explicit handler must opt them out, otherwise preventDefault and
        // pointer capture below would swallow the click. This also covers controls that sit within EDGE_MARGIN of a
        // border (e.g. the close button near the top-right), which would otherwise start an edge resize.
        if (target?.closest(IrisChatbotWidgetComponent.INTERACTIVE_CONTROL_SELECTOR)) {
            return;
        }
        const rect = widget.getBoundingClientRect();
        const edges = this.detectResizeEdges(event, rect, target);
        const resizing = edges.left || edges.right || edges.top || edges.bottom;
        const dragging = !resizing && !!target?.closest('.chat-header');
        if (!resizing && !dragging) {
            return;
        }
        event.preventDefault();

        this.gesture = resizing ? 'resize' : 'drag';
        this.activePointerId = event.pointerId;
        this.resizeEdges = edges;
        this.startPointerX = event.clientX;
        this.startPointerY = event.clientY;
        this.startWidth = rect.width;
        this.startHeight = rect.height;
        this.startTranslateX = parseFloat(widget.getAttribute('data-x') ?? '') || 0;
        this.startTranslateY = parseFloat(widget.getAttribute('data-y') ?? '') || 0;

        widget.setPointerCapture?.(event.pointerId);
        // Hold the gesture cursor for the whole drag (pointer capture keeps it even when the pointer leaves the widget).
        this.setWidgetCursor(this.gesture === 'resize' ? this.resizeCursor(edges) : 'grabbing');
        const move = (e: PointerEvent) => this.onPointerMove(e);
        const up = (e: PointerEvent) => {
            if (e.pointerId !== this.activePointerId) {
                return;
            }
            this.releaseCaptureSafely(widget, e.pointerId);
            this.endGesture();
        };
        const unMove = this.renderer.listen(widget, 'pointermove', move);
        const unUp = this.renderer.listen(widget, 'pointerup', up);
        const unCancel = this.renderer.listen(widget, 'pointercancel', up);
        this.gestureCleanup = () => {
            unMove();
            unUp();
            unCancel();
        };
    }

    private onPointerMove(event: PointerEvent): void {
        const widget = this.widgetEl;
        if (!this.gesture || !widget || event.pointerId !== this.activePointerId) {
            return;
        }
        const containerRect = this.document.querySelector<HTMLElement>('.cdk-overlay-container')?.getBoundingClientRect();
        const dx = event.clientX - this.startPointerX;
        const dy = event.clientY - this.startPointerY;

        if (this.gesture === 'drag') {
            let x = this.startTranslateX + dx;
            let y = this.startTranslateY + dy;
            if (containerRect) {
                x = Math.max(0, Math.min(x, containerRect.width - this.startWidth));
                y = Math.max(0, Math.min(y, containerRect.height - this.startHeight));
            }
            this.applyTransform(widget, x, y);
            return;
        }

        let width = this.startWidth;
        let height = this.startHeight;
        let x = this.startTranslateX;
        let y = this.startTranslateY;
        const rightAnchor = this.startTranslateX + this.startWidth;
        const bottomAnchor = this.startTranslateY + this.startHeight;

        if (this.resizeEdges.right) {
            width = Math.max(this.initialWidth, this.startWidth + dx);
        }
        if (this.resizeEdges.left) {
            width = Math.max(this.initialWidth, this.startWidth - dx);
            x = rightAnchor - width;
        }
        if (this.resizeEdges.bottom) {
            height = Math.max(this.initialHeight, this.startHeight + dy);
        }
        if (this.resizeEdges.top) {
            height = Math.max(this.initialHeight, this.startHeight - dy);
            y = bottomAnchor - height;
        }

        // Keep the widget edges inside the overlay container (interact.js restrictEdges).
        if (containerRect) {
            if (this.resizeEdges.left && x < 0) {
                x = 0;
                width = rightAnchor;
            }
            if (this.resizeEdges.top && y < 0) {
                y = 0;
                height = bottomAnchor;
            }
            if (this.resizeEdges.right && x + width > containerRect.width) {
                width = containerRect.width - x;
            }
            if (this.resizeEdges.bottom && y + height > containerRect.height) {
                height = containerRect.height - y;
            }
        }

        this.renderer.setStyle(widget, 'width', `${width}px`);
        this.renderer.setStyle(widget, 'height', `${height}px`);
        this.applyTransform(widget, x, y);

        if (containerRect) {
            // Reset fullSize when the widget is smaller than the full-size factors times the container.
            this.fullSize.set(!(width < containerRect.width * this.fullWidthFactor || height < containerRect.height * this.fullHeightFactor));
        }
    }

    private applyTransform(widget: HTMLElement, x: number, y: number): void {
        this.renderer.setStyle(widget, 'transform', `translate(${x}px, ${y}px)`);
        widget.setAttribute('data-x', String(x));
        widget.setAttribute('data-y', String(y));
    }

    private endGesture(): void {
        this.gesture = undefined;
        this.activePointerId = undefined;
        this.gestureCleanup?.();
        this.gestureCleanup = undefined;
        // Drop the gesture cursor; the next hover move (or the header's CSS grab cursor) takes over.
        this.setWidgetCursor('');
    }

    /**
     * Releases the pointer capture only while the widget still holds it. On `pointercancel` the browser has
     * already released the capture, so calling releasePointerCapture() again throws InvalidPointerId; that throw
     * would abort gesture teardown and leave the widget stuck following the pointer (with its listeners leaked).
     * The hasPointerCapture guard plus try/catch make this safe to call from pointerup, pointercancel and destroy.
     */
    private releaseCaptureSafely(widget: HTMLElement, pointerId: number): void {
        try {
            if (widget.hasPointerCapture?.(pointerId)) {
                widget.releasePointerCapture(pointerId);
            }
        } catch {
            // The capture was already released (e.g. on pointercancel); nothing to do.
        }
    }

    setPositionAndScale() {
        const nE = this.widgetEl ?? (this.document.querySelector('.chat-widget') as HTMLElement | null);
        const cntRect = (this.document.querySelector('.cdk-overlay-container') as HTMLElement)?.getBoundingClientRect();
        if (!cntRect || !nE) {
            return;
        }

        let initX: number;
        let initY: number;

        if (this.fullSize() || this.isMobile()) {
            initX = (cntRect.width * (1 - this.fullWidthFactor)) / 2.0;
            initY = (cntRect.height * (1 - this.fullHeightFactor)) / 2.0;
        } else {
            initX = cntRect.width - this.initialWidth - 20;
            initY = cntRect.height - this.initialHeight - 20;
        }

        nE.style.transform = `translate(${initX}px, ${initY}px)`;
        nE.setAttribute('data-x', String(initX));
        nE.setAttribute('data-y', String(initY));

        // Set width and height
        if (this.fullSize() || this.isMobile()) {
            nE.style.width = `${cntRect.width * this.fullWidthFactor}px`;
            nE.style.height = `${cntRect.height * this.fullHeightFactor}px`;
        } else {
            nE.style.width = `${this.initialWidth}px`;
            nE.style.height = `${this.initialHeight}px`;
        }
    }

    ngOnDestroy() {
        // If the widget is torn down mid-gesture, release any capture it still holds before dropping the listeners.
        if (this.widgetEl && this.activePointerId !== undefined) {
            this.releaseCaptureSafely(this.widgetEl, this.activePointerId);
        }
        this.pointerDownCleanup?.();
        this.hoverMoveCleanup?.();
        this.hoverLeaveCleanup?.();
        this.gestureCleanup?.();
        this.toggleScrollLock(false);
    }

    /**
     * Closes the chat widget.
     */
    closeChat() {
        this.dialog.closeAll();
    }

    toggleFullSize() {
        this.fullSize.update((v) => !v);
        this.setPositionAndScale();
    }

    toggleScrollLock(lockParent: boolean): void {
        if (lockParent) {
            document.body.classList.add('cdk-global-scroll');
        } else {
            document.body.classList.remove('cdk-global-scroll');
        }
    }

    /**
     * Closes the chat widget and signals that it should reopen after LLM selection.
     */
    reopenDialog() {
        this.chatService.setShouldReopenChat(true);
        this.dialog.closeAll();
    }
}
