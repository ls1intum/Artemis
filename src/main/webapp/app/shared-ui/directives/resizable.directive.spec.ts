import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
            (resizeStart)="onStart()"
            (resizeMove)="onResize($event)"
            (resizeEnd)="onEnd($event)"
        >
            <div class="draggable-left"></div>
            <div class="draggable-right"></div>
        </div>
    `,
})
class ResizableTestHostComponent {
    readonly edges = signal<ResizableEdges>({ left: '.draggable-left' });
    readonly constraints = signal<ResizableConstraints>({ minWidth: 100, maxWidth: 400 });
    readonly enabled = signal(true);
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

/** jsdom has no PointerEvent constructor; a MouseEvent carries clientX/clientY/button plus the pointer fields the directive reads. */
function pointer(target: Element, type: string, clientX: number, clientY: number, pointerId = 1): void {
    const event = new MouseEvent(type, { bubbles: true, cancelable: true, clientX, clientY, button: 0 });
    Object.defineProperties(event, { pointerId: { value: pointerId }, pointerType: { value: 'mouse' } });
    target.dispatchEvent(event);
}

describe('ResizableDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ResizableTestHostComponent>;
    let host: HTMLElement;
    let panel: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [ResizableTestHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(ResizableTestHostComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
        panel = host.querySelector('.panel') as HTMLElement;
        // jsdom returns an all-zero rect; pin the starting size so the resize math has a baseline.
        panel.getBoundingClientRect = () => ({ width: 200, height: 100, left: 100, top: 0, right: 300, bottom: 100, x: 100, y: 0, toJSON: () => ({}) }) as DOMRect;
    });

    it('grows the width when dragging the left handle leftwards and writes inline width', () => {
        const handle = panel.querySelector('.draggable-left')!;
        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', 60, 50); // moved 40px left -> +40 width

        expect(fixture.componentInstance.starts).toBe(1);
        expect(fixture.componentInstance.lastResize).toEqual({ width: 240, height: 100 });
        expect(panel.style.width).toBe('240px');
        expect(panel.classList.contains('card-resizable')).toBe(true);

        pointer(panel, 'pointerup', 60, 50);
        expect(panel.classList.contains('card-resizable')).toBe(false);
        expect(fixture.componentInstance.lastEnd).toBeDefined();
    });

    it('clamps to the configured min and max width', () => {
        const handle = panel.querySelector('.draggable-left')!;
        // Drag far right -> width would shrink below min (100) -> clamped to 100.
        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', 250, 50); // -150 -> 50, clamped to 100
        expect(fixture.componentInstance.lastResize!.width).toBe(100);

        pointer(panel, 'pointerup', 250, 50);

        // Drag far left -> width would exceed max (400) -> clamped to 400.
        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', -400, 50); // +500 -> 700, clamped to 400
        expect(fixture.componentInstance.lastResize!.width).toBe(400);
    });

    it('does nothing while disabled', () => {
        fixture.componentInstance.enabled.set(false);
        fixture.detectChanges();
        const handle = panel.querySelector('.draggable-left')!;
        pointer(handle, 'pointerdown', 100, 50);
        pointer(panel, 'pointermove', 60, 50);
        expect(fixture.componentInstance.starts).toBe(0);
        expect(panel.style.width).toBe('');
    });

    it('resizes height from the bottom edge', () => {
        fixture.componentInstance.edges.set({ bottom: '.draggable-right' });
        fixture.componentInstance.constraints.set({ minHeight: 50, maxHeight: 500 });
        fixture.detectChanges();
        const handle = panel.querySelector('.draggable-right')!;
        pointer(handle, 'pointerdown', 50, 100);
        pointer(panel, 'pointermove', 50, 180); // +80 height
        expect(fixture.componentInstance.lastResize).toEqual({ width: 200, height: 180 });
        expect(panel.style.height).toBe('180px');
    });
});
