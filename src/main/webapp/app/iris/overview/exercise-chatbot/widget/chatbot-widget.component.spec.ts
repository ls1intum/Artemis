import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BreakpointObserver, BreakpointState, Breakpoints } from '@angular/cdk/layout';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisChatbotWidgetComponent } from 'app/iris/overview/exercise-chatbot/widget/chatbot-widget.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { MockIrisBaseChatbotComponent } from 'app/iris/overview/exercise-chatbot/widget/chatbot-widget.component.mock';
import { MockProvider } from 'ng-mocks';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { NavigationStart, Router } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { By } from '@angular/platform-browser';

/** jsdom has no PointerEvent constructor; a MouseEvent carries clientX/clientY/button plus the pointer fields the component reads. */
function pointer(target: EventTarget, type: string, clientX: number, clientY: number, pointerId = 1): void {
    const event = new MouseEvent(type, { bubbles: true, cancelable: true, clientX, clientY, button: 0 });
    Object.defineProperties(event, { pointerId: { value: pointerId }, pointerType: { value: 'mouse' } });
    target.dispatchEvent(event);
}

describe('IrisChatbotWidgetComponent', () => {
    let component: IrisChatbotWidgetComponent;
    let fixture: ComponentFixture<IrisChatbotWidgetComponent>;
    let breakpoint$: BehaviorSubject<BreakpointState>;
    let dialogRef: DynamicDialogRef;
    let routerEvents$: Subject<unknown>;

    beforeEach(async () => {
        routerEvents$ = new Subject<unknown>();
        breakpoint$ = new BehaviorSubject<BreakpointState>({
            matches: false,
            breakpoints: { [Breakpoints.Handset]: false },
        });
        await TestBed.configureTestingModule({
            imports: [IrisChatbotWidgetComponent],
            providers: [
                MockProvider(IrisChatService),
                { provide: DynamicDialogRef, useValue: { close: vi.fn() } },
                { provide: Router, useValue: { events: routerEvents$.asObservable() } },
                {
                    provide: BreakpointObserver,
                    useValue: {
                        observe: () => breakpoint$.asObservable(),
                        isMatched: (query: string | string[]) => false,
                    },
                },
            ],
        })
            .overrideComponent(IrisChatbotWidgetComponent, {
                remove: { imports: [IrisBaseChatbotComponent] },
                add: { imports: [MockIrisBaseChatbotComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisChatbotWidgetComponent);
        component = fixture.componentInstance;

        dialogRef = TestBed.inject(DynamicDialogRef);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should close the dialog when closeChat is called', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.closeChat();
        expect(closeSpy).toHaveBeenCalled();
    });

    it('should close the dialog on NavigationStart', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');

        routerEvents$.next(new NavigationStart(1, '/somewhere'));

        expect(closeSpy).toHaveBeenCalled();
    });

    it('should toggle fullSize and call setPositionAndScale when toggleFullSize is called', () => {
        const setPositionAndScaleSpy = vi.spyOn(component, 'setPositionAndScale');
        const initialFullSize = component.fullSize();
        component.toggleFullSize();
        expect(component.fullSize()).toBe(!initialFullSize);
        expect(setPositionAndScaleSpy).toHaveBeenCalled();
    });

    it('should add or remove cdk-global-scroll class when toggleScrollLock is called', () => {
        component.toggleScrollLock(true);
        expect(document.body.classList.contains('cdk-global-scroll')).toBe(true);
        component.toggleScrollLock(false);
        expect(document.body.classList.contains('cdk-global-scroll')).toBe(false);
    });

    it('should call onResize when window is resized', () => {
        const spy = vi.spyOn(component, 'onResize');
        window.dispatchEvent(new Event('resize'));
        expect(spy).toHaveBeenCalled();
    });

    it('should call setPositionAndScale on initialization', () => {
        const spy = vi.spyOn(component, 'setPositionAndScale');
        component.ngAfterViewInit();
        expect(spy).toHaveBeenCalled();
    });

    it('should add or remove cdk-global-scroll class when mouse enters or leaves container', () => {
        const spy = vi.spyOn(component, 'toggleScrollLock');
        const container = fixture.debugElement.query(By.css('.container'));

        container.triggerEventHandler('mouseenter', null);
        fixture.detectChanges();
        expect(document.body.classList.contains('cdk-global-scroll')).toBe(true);
        expect(spy).toHaveBeenCalledWith(true);

        container.triggerEventHandler('mouseleave', null);
        fixture.detectChanges();
        expect(document.body.classList.contains('cdk-global-scroll')).toBe(false);
        expect(spy).toHaveBeenCalledWith(false);
    });

    it('should set isMobile to true when overlay container width is less than 600', () => {
        breakpoint$.next({
            matches: true,
            breakpoints: { [Breakpoints.Handset]: true },
        });
        // Setup DOM — the widget now lives in a PrimeNG DynamicDialog, so its bounding container
        // is the dialog mask wrapper (.p-dialog-mask).
        const overlay = document.createElement('div');
        overlay.className = 'p-dialog-mask';

        overlay.getBoundingClientRect = vi.fn(() => ({
            x: 0,
            y: 0,
            width: 500,
            height: 600,
            top: 0,
            right: 500,
            bottom: 600,
            left: 0,
            toJSON: () => {},
        }));
        document.body.appendChild(overlay);

        const widget = document.createElement('div');
        widget.className = 'chat-widget';
        document.body.appendChild(widget);

        component.setPositionAndScale();

        expect(component.isMobile()).toBe(true);

        // Clean up
        document.body.removeChild(overlay);
        document.body.removeChild(widget);
    });

    it('should not throw if the dialog mask or chat-widget is missing in setPositionAndScale', () => {
        expect(() => component.setPositionAndScale()).not.toThrow();
    });

    /** Builds the dialog mask container + a chat widget (with header) in the DOM and wires the pointer handlers. */
    function setupWidget(rect: { left: number; top: number; width: number; height: number }): { overlay: HTMLElement; widget: HTMLElement; header: HTMLElement } {
        // Ensure the component binds to OUR test widget (not the one rendered by the fixture template).
        document.querySelectorAll('.chat-widget, .p-dialog-mask, .cdk-overlay-container').forEach((el) => el.remove());
        const overlay = document.createElement('div');
        overlay.className = 'p-dialog-mask';
        overlay.getBoundingClientRect = vi.fn(() => ({ x: 0, y: 0, width: 1000, height: 1000, top: 0, right: 1000, bottom: 1000, left: 0, toJSON: () => {} }));
        document.body.appendChild(overlay);

        const widget = document.createElement('div');
        widget.className = 'chat-widget';
        widget.setAttribute('data-x', String(rect.left - 100));
        widget.setAttribute('data-y', String(rect.top - 80));
        widget.getBoundingClientRect = vi.fn(
            () =>
                ({
                    x: rect.left,
                    y: rect.top,
                    left: rect.left,
                    top: rect.top,
                    width: rect.width,
                    height: rect.height,
                    right: rect.left + rect.width,
                    bottom: rect.top + rect.height,
                    toJSON: () => {},
                }) as DOMRect,
        );
        const header = document.createElement('div');
        header.className = 'chat-header';
        widget.appendChild(header);
        document.body.appendChild(widget);

        component.ngAfterViewInit();
        return { overlay, widget, header };
    }

    it('resizes the widget from the right edge, clamping to the minimum width', () => {
        const { overlay, widget } = setupWidget({ left: 110, top: 100, width: 600, height: 600 });
        // ngAfterViewInit -> setPositionAndScale repositions the widget; pin a known translate for the math.
        widget.setAttribute('data-x', '10');
        widget.setAttribute('data-y', '20');

        // pointerdown within EDGE_MARGIN of the right border (right = 710) -> right-edge resize. startWidth 600 -> right anchor 610.
        pointer(widget, 'pointerdown', 705, 400);
        pointer(widget, 'pointermove', 755, 400); // +50 -> width 650
        expect(widget.style.width).toBe('650px');
        expect(widget.style.transform).toBe('translate(10px, 20px)'); // x unchanged on right-edge resize
        expect(component.fullSize()).toBe(false); // 650 < 1000 * 0.93

        // shrink well past the minimum (initialWidth 450) -> clamped
        pointer(widget, 'pointermove', 405, 400); // -300 -> 300, clamped to 450
        expect(widget.style.width).toBe('450px');
        pointer(widget, 'pointerup', 405, 400);

        overlay.remove();
        widget.remove();
    });

    it('shows a resize cursor when hovering near a border and clears it in the body', () => {
        // Regression: the in-house resize replaced interact.js, which changed the cursor near a resizable edge.
        // Without a hover affordance users cannot tell the widget is resizable.
        const { overlay, widget } = setupWidget({ left: 110, top: 100, width: 600, height: 600 }); // right = 710, bottom = 700

        pointer(widget, 'pointermove', 705, 400); // within 10px of the right border
        expect(widget.style.cursor).toBe('ew-resize');

        pointer(widget, 'pointermove', 400, 695); // within 10px of the bottom border
        expect(widget.style.cursor).toBe('ns-resize');

        pointer(widget, 'pointermove', 705, 695); // bottom-right corner
        expect(widget.style.cursor).toBe('nwse-resize');

        pointer(widget, 'pointermove', 400, 400); // interior -> no resize affordance
        expect(widget.style.cursor).toBe('');

        overlay.remove();
        widget.remove();
    });

    it('drags the widget from the header and keeps it inside the overlay container', () => {
        const { overlay, widget, header } = setupWidget({ left: 110, top: 100, width: 450, height: 600 });
        widget.setAttribute('data-x', '10');
        widget.setAttribute('data-y', '20');

        // pointerdown on the header, away from all edges -> drag.
        pointer(header, 'pointerdown', 300, 300);
        pointer(widget, 'pointermove', 330, 295); // dx +30, dy -5 -> translate from (10,20) to (40,15)
        expect(widget.style.transform).toBe('translate(40px, 15px)');
        expect(widget.getAttribute('data-x')).toBe('40');
        expect(widget.getAttribute('data-y')).toBe('15');
        pointer(widget, 'pointerup', 330, 295);

        overlay.remove();
        widget.remove();
    });

    it('does not stay stuck after a cancelled gesture (pointercancel) and can be dragged again', () => {
        // Regression: the gesture-end handler released the pointer capture unconditionally. On pointercancel the
        // browser has already released it, so releasePointerCapture() throws InvalidPointerId, which aborted the
        // teardown and left the widget permanently following the pointer (with its listeners leaked).
        const { overlay, widget, header } = setupWidget({ left: 110, top: 100, width: 450, height: 600 });
        widget.setAttribute('data-x', '10');
        widget.setAttribute('data-y', '20');

        // jsdom no-ops pointer capture; reproduce the real browser: on pointercancel the capture is already gone,
        // so hasPointerCapture() is false and an unguarded releasePointerCapture() call would throw.
        widget.setPointerCapture = vi.fn();
        widget.hasPointerCapture = vi.fn(() => false);
        widget.releasePointerCapture = vi.fn(() => {
            throw new DOMException('InvalidPointerId', 'NotFoundError');
        });

        // Drag, then let the browser cancel the gesture mid-drag.
        pointer(header, 'pointerdown', 300, 300);
        pointer(widget, 'pointermove', 330, 295); // -> translate(40px, 15px)
        expect(widget.style.transform).toBe('translate(40px, 15px)');
        pointer(widget, 'pointercancel', 330, 295);

        // The gesture must have torn down: a stray move no longer drags the widget.
        pointer(widget, 'pointermove', 500, 500);
        expect(widget.style.transform).toBe('translate(40px, 15px)');

        // And a fresh gesture works again (proves the widget is not wedged).
        pointer(header, 'pointerdown', 300, 300);
        pointer(widget, 'pointermove', 320, 300); // dx +20 from (40,15) -> (60,15)
        expect(widget.style.transform).toBe('translate(60px, 15px)');
        pointer(widget, 'pointerup', 320, 300);

        overlay.remove();
        widget.remove();
    });

    it('does not start a drag when the pointerdown lands on a header control, so the control keeps its click', () => {
        // Regression: the drag/resize handler used to start a drag (and preventDefault) on any pointerdown inside
        // .chat-header, which swallowed clicks on the header controls (info / new chat / maximize / close).
        const { overlay, widget, header } = setupWidget({ left: 110, top: 100, width: 450, height: 600 });
        widget.setAttribute('data-x', '10');
        widget.setAttribute('data-y', '20');

        // A header control button, away from all widget edges (so only the interactive-target rule can apply).
        const button = document.createElement('button');
        button.className = 'header-control';
        header.appendChild(button);

        const transformBefore = widget.style.transform;
        const down = new MouseEvent('pointerdown', { bubbles: true, cancelable: true, clientX: 300, clientY: 300, button: 0 });
        Object.defineProperties(down, { pointerId: { value: 1 }, pointerType: { value: 'mouse' } });
        button.dispatchEvent(down);
        pointer(widget, 'pointermove', 330, 295); // would translate to (40,15) if a drag had started

        // No gesture started: the widget did not move, and the pointerdown was not preventDefaulted, so the click proceeds.
        expect(widget.getAttribute('data-x')).toBe('10');
        expect(widget.getAttribute('data-y')).toBe('20');
        expect(widget.style.transform).toBe(transformBefore);
        expect(down.defaultPrevented).toBe(false);

        overlay.remove();
        widget.remove();
    });
});
