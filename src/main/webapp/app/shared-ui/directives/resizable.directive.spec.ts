import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ResizableConstraints, ResizableDirective, ResizableEdges, ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';

@Component({
    selector: 'jhi-resizable-test-host',
    imports: [ResizableDirective],
    template: `
        <div
            class="panel"
            jhiResizable
            [resizableEdges]="edges()"
            [resizableConstraints]="constraints()"
            [resizableEnabled]="enabled()"
            [resizableApplyInlineSize]="applyInline()"
            (resizeStart)="onStart()"
            (resizeMove)="onResize($event)"
            (resizeEnd)="onEnd($event)"
        >
            <div class="draggable-left" aria-label="Resize panel"></div>
            <div class="draggable-right" aria-label="Resize panel"></div>
            <button type="button" class="button-handle" aria-label="Reset split ratio"></button>
            @if (showLateHandle()) {
                <div class="late-handle" aria-label="Resize panel"></div>
            }
        </div>
    `,
})
class ResizableTestHostComponent {
    readonly edges = signal<ResizableEdges>({ left: '.draggable-left' });
    readonly constraints = signal<ResizableConstraints>({ minWidth: 100, maxWidth: 400 });
    readonly enabled = signal(true);
    readonly applyInline = signal(true);
    readonly showLateHandle = signal(false);
    starts = 0;
    lastResize?: ResizableSizeEvent;
    lastEnd?: ResizableSizeEvent;
    onStart(): void {
        this.starts++;
    }
    onResize(e: ResizableSizeEvent): void {
        this.lastResize = e;
    }
    onEnd(e: ResizableSizeEvent): void {
        this.lastEnd = e;
    }
}

@Component({
    selector: 'jhi-resizable-external-host',
    imports: [ResizableDirective],
    template: `
        <div class="wrapper">
            <div
                class="panel"
                jhiResizable
                [resizableEdges]="{ right: '.outside-handle' }"
                [resizableConstraints]="{ minWidth: 100, maxWidth: 400 }"
                [resizableApplyInlineSize]="false"
                [resizableHandleOutsideHost]="true"
                (resizeMove)="onResize($event)"
            ></div>
            <div class="outside-handle" aria-label="Resize panel"></div>
        </div>
    `,
})
class ResizableExternalHostComponent {
    lastResize?: ResizableSizeEvent;
    onResize(e: ResizableSizeEvent): void {
        this.lastResize = e;
    }
}

function pointer(target: Element, type: string, clientX: number, clientY: number, pointerId = 1): void {
    const event = new MouseEvent(type, { bubbles: true, cancelable: true, clientX, clientY, button: 0 });
    Object.defineProperties(event, { pointerId: { value: pointerId }, pointerType: { value: 'mouse' } });
    target.dispatchEvent(event);
}

describe('ResizableDirective', () => {
    let fixture: ComponentFixture<ResizableTestHostComponent>;
    let host: HTMLElement;
    let panel: HTMLElement;
    let resizeObservers: Array<{ callback: ResizeObserverCallback; observe: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn> }>;
    const originalResizeObserver = globalThis.ResizeObserver;

    beforeEach(async () => {
        resizeObservers = [];
        globalThis.ResizeObserver = class {
            readonly observe = vi.fn();
            readonly disconnect = vi.fn();
            readonly unobserve = vi.fn();

            constructor(callback: ResizeObserverCallback) {
                resizeObservers.push({ callback, observe: this.observe, disconnect: this.disconnect });
            }
        } as unknown as typeof ResizeObserver;
        await TestBed.configureTestingModule({ imports: [ResizableTestHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(ResizableTestHostComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
        panel = host.querySelector('.panel') as HTMLElement;
        panel.getBoundingClientRect = () => ({ width: 200, height: 100, left: 100, top: 0, right: 300, bottom: 100, x: 100, y: 0, toJSON: () => ({}) }) as DOMRect;
        fixture.componentInstance.constraints.set({ minWidth: 100, maxWidth: 400 });
        fixture.detectChanges();
    });

    afterEach(() => {
        globalThis.ResizeObserver = originalResizeObserver;
    });

    it('refreshes handle metadata when the host resizes and disconnects the observer on destroy', () => {
        const directive = fixture.debugElement.query(By.directive(ResizableDirective)).injector.get(ResizableDirective);
        const observer = resizeObservers.find(({ observe }) => observe.mock.calls.some(([target]) => target === panel))!;
        const scheduleHandleStyles = vi.spyOn(directive as any, 'scheduleHandleStyles');

        expect(observer.observe).toHaveBeenCalledExactlyOnceWith(panel);
        observer.callback([], {} as ResizeObserver);
        expect(scheduleHandleStyles).toHaveBeenCalledOnce();

        fixture.destroy();
        expect(observer.disconnect).toHaveBeenCalledOnce();
    });

    it('grows the width when dragging the left handle leftwards and writes inline width', () => {
        const handle = panel.querySelector('.draggable-left')!;
        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', 60, 50);

        expect(fixture.componentInstance.starts).toBe(1);
        expect(fixture.componentInstance.lastResize).toEqual({ width: 240, height: 100 });
        expect(panel.style.width).toBe('240px');
        expect(panel.classList.contains('card-resizable')).toBe(true);

        pointer(panel, 'pointerup', 60, 50);
        expect(panel.classList.contains('card-resizable')).toBe(false);
        expect(fixture.componentInstance.lastEnd).toBeDefined();
    });

    it('sets a resize cursor on the handle and suppresses body text selection during the drag', async () => {
        await fixture.whenStable();
        const handle = panel.querySelector('.draggable-left') as HTMLElement;
        expect(handle.style.cursor).toBe('col-resize');

        pointer(handle, 'pointerdown', 100, 50);
        expect(document.body.style.userSelect).toBe('none');

        pointer(panel, 'pointerup', 100, 50);
        expect(document.body.style.userSelect).toBe('');
    });

    it('exposes separator semantics and supports keyboard resizing', async () => {
        await fixture.whenStable();
        const handle = panel.querySelector('.draggable-left') as HTMLElement;

        expect(handle.getAttribute('role')).toBe('separator');
        expect(handle.getAttribute('tabindex')).toBe('0');
        expect(handle.getAttribute('aria-orientation')).toBe('vertical');
        expect(handle.getAttribute('aria-controls')).toBe(panel.id);
        expect(handle.getAttribute('aria-valuemin')).toBe('100');
        expect(handle.getAttribute('aria-valuemax')).toBe('400');
        expect(handle.getAttribute('aria-valuenow')).toBe('200');

        handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true, cancelable: true }));
        expect(panel.style.width).toBe('216px');
        expect(handle.getAttribute('aria-valuenow')).toBe('216');
        expect(fixture.componentInstance.lastResize).toEqual({ width: 216, height: 100 });
        expect(fixture.componentInstance.lastEnd).toEqual({ width: 216, height: 100 });

        handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true, cancelable: true }));
        expect(panel.style.width).toBe('100px');

        handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true, cancelable: true }));
        expect(panel.style.width).toBe('400px');
    });

    it('leaves a handle that is already an interactive control announced as that control', async () => {
        fixture.componentInstance.edges.set({ left: '.button-handle' });
        fixture.detectChanges();
        await fixture.whenStable();

        const handle = panel.querySelector('.button-handle') as HTMLElement;
        expect(handle.getAttribute('role')).toBeNull();
        expect(handle.getAttribute('aria-valuenow')).toBeNull();
        expect(handle.getAttribute('aria-controls')).toBeNull();
        expect(handle.style.cursor).toBe('col-resize');
    });

    it('re-applies handle styles when the edge map changes, before any pointerdown', async () => {
        await fixture.whenStable();
        const left = panel.querySelector('.draggable-left') as HTMLElement;
        const right = panel.querySelector('.draggable-right') as HTMLElement;
        expect(right.style.cursor).toBe('');

        fixture.componentInstance.edges.set({ left: '.draggable-left', right: '.draggable-right' });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(right.style.cursor).toBe('col-resize');
        expect(right.style.touchAction).toBe('none');
        expect(right.getAttribute('role')).toBe('separator');
        expect(right.getAttribute('aria-orientation')).toBe('vertical');

        fixture.componentInstance.edges.set({ right: '.draggable-right' });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(left.style.cursor).toBe('');
        expect(left.style.touchAction).toBe('');
        expect(left.getAttribute('role')).toBeNull();
        expect(left.getAttribute('tabindex')).toBeNull();
        expect(left.getAttribute('aria-orientation')).toBeNull();
    });

    it('makes a late-rendered handle keyboard accessible before interaction', async () => {
        fixture.componentInstance.edges.set({ left: '.late-handle' });
        fixture.componentInstance.showLateHandle.set(true);
        fixture.detectChanges();
        await fixture.whenStable();

        const handle = panel.querySelector('.late-handle') as HTMLElement;
        await vi.waitFor(() => expect(handle.getAttribute('role')).toBe('separator'));
        expect(handle.getAttribute('tabindex')).toBe('0');
        expect(handle.style.cursor).toBe('col-resize');
    });

    it('clamps to the configured min and max width', () => {
        const handle = panel.querySelector('.draggable-left')!;
        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', 250, 50);
        expect(fixture.componentInstance.lastResize!.width).toBe(100);

        pointer(panel, 'pointerup', 250, 50);

        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', -400, 50);
        expect(fixture.componentInstance.lastResize!.width).toBe(400);
    });

    it('makes the resize handle inert and unfocusable while disabled', async () => {
        fixture.componentInstance.enabled.set(false);
        fixture.detectChanges();
        await fixture.whenStable();
        const handle = panel.querySelector('.draggable-left') as HTMLElement;
        expect(handle.getAttribute('aria-disabled')).toBe('true');
        expect(handle.getAttribute('tabindex')).toBe('-1');
        expect(handle.style.cursor).toBe('default');
        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', 60, 50);
        expect(fixture.componentInstance.starts).toBe(0);
        expect(panel.style.width).toBe('');

        fixture.componentInstance.enabled.set(true);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(handle.getAttribute('aria-disabled')).toBeNull();
        expect(handle.getAttribute('tabindex')).toBe('0');
    });

    it('resizes height from the bottom edge with pointer and keyboard input', async () => {
        fixture.componentInstance.edges.set({ bottom: '.draggable-right' });
        fixture.componentInstance.constraints.set({ minHeight: 50, maxHeight: 500 });
        fixture.detectChanges();
        await fixture.whenStable();
        const handle = panel.querySelector('.draggable-right') as HTMLElement;
        pointer(handle, 'pointerdown', 50, 100);
        pointer(panel, 'pointermove', 50, 180);
        expect(fixture.componentInstance.lastResize).toEqual({ width: 200, height: 180 });
        expect(panel.style.height).toBe('180px');

        panel.style.removeProperty('height');
        handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        expect(panel.style.width).toBe('');
        expect(panel.style.height).toBe('116px');
        expect(handle.getAttribute('aria-orientation')).toBe('horizontal');
    });

    it('grows the width when dragging the right handle rightwards', () => {
        fixture.componentInstance.edges.set({ right: '.draggable-right' });
        fixture.detectChanges();
        const handle = panel.querySelector('.draggable-right')!;
        pointer(handle, 'pointerdown', 300, 50);
        pointer(panel, 'pointermove', 360, 50);

        expect(fixture.componentInstance.lastResize).toEqual({ width: 260, height: 100 });
        expect(panel.style.width).toBe('260px');
    });

    it('emits the size but writes no inline width when resizableApplyInlineSize is false', () => {
        fixture.componentInstance.applyInline.set(false);
        fixture.detectChanges();
        const handle = panel.querySelector('.draggable-left')!;
        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', 60, 50);

        expect(fixture.componentInstance.lastResize).toEqual({ width: 240, height: 100 });
        expect(panel.style.width).toBe('');
    });

    it('cleans up without throwing when the drag ends with pointercancel', () => {
        panel.hasPointerCapture = () => true;
        panel.releasePointerCapture = () => {
            throw new DOMException('InvalidPointerId', 'NotFoundError');
        };
        const handle = panel.querySelector('.draggable-left')!;
        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', 60, 50);
        expect(panel.classList.contains('card-resizable')).toBe(true);

        expect(() => pointer(panel, 'pointercancel', 60, 50)).not.toThrow();
        expect(panel.classList.contains('card-resizable')).toBe(false);

        fixture.componentInstance.lastResize = undefined;
        pointer(panel, 'pointermove', 0, 50);
        expect(fixture.componentInstance.lastResize).toBeUndefined();
    });

    it('releases the pointer capture and stops resizing when destroyed mid-drag', () => {
        let released = false;
        panel.hasPointerCapture = () => true;
        panel.releasePointerCapture = () => {
            released = true;
        };
        const handle = panel.querySelector('.draggable-left')!;
        pointer(handle, 'pointerdown', 100, 50);

        expect(() => fixture.destroy()).not.toThrow();
        expect(released).toBe(true);

        fixture.componentInstance.lastResize = undefined;
        pointer(panel, 'pointermove', 0, 50);
        expect(fixture.componentInstance.lastResize).toBeUndefined();
    });

    it('resizes from a handle outside the host when resizableHandleOutsideHost is set', async () => {
        const externalFixture = TestBed.createComponent(ResizableExternalHostComponent);
        const externalHost = externalFixture.nativeElement as HTMLElement;
        externalFixture.detectChanges();
        await externalFixture.whenStable();
        const externalPanel = externalHost.querySelector('.panel') as HTMLElement;
        externalPanel.getBoundingClientRect = () => ({ width: 200, height: 100, left: 100, top: 0, right: 300, bottom: 100, x: 100, y: 0, toJSON: () => ({}) }) as DOMRect;

        const handle = externalHost.querySelector('.outside-handle')!;
        pointer(handle, 'pointerdown', 300, 50);
        pointer(externalPanel, 'pointermove', 360, 50);

        expect(externalFixture.componentInstance.lastResize).toEqual({ width: 260, height: 100 });

        handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true, cancelable: true }));
        expect(externalFixture.componentInstance.lastResize).toEqual({ width: 400, height: 100 });
    });
});
