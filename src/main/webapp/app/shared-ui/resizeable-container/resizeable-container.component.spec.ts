import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';

function pointer(target: Element, type: string, clientX: number, clientY: number): void {
    const event = new MouseEvent(type, { bubbles: true, cancelable: true, clientX, clientY, button: 0 });
    Object.defineProperties(event, { pointerId: { value: 1 }, pointerType: { value: 'mouse' } });
    target.dispatchEvent(event);
}

describe('ResizeableContainerComponent', () => {
    let component: ResizeableContainerComponent;
    let fixture: ComponentFixture<ResizeableContainerComponent>;

    const divider = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="resizeable-container-divider"]');

    beforeEach(() => {
        TestBed.configureTestingModule({}).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ResizeableContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('divider', () => {
        it('is a plain handle without a grip icon, so that the stylesheet alone sizes it', () => {
            const handle = divider();

            expect(handle).not.toBeNull();
            expect(handle!.classList.contains('draggable-left')).toBe(true);
            expect(handle!.children).toHaveLength(0);
            expect(handle!.textContent).toBe('');
        });

        it('is handed to the resizable directive, which gives it the resize cursor', async () => {
            await fixture.whenStable();

            expect(divider()!.style.cursor).toBe('col-resize');
        });

        it('resizes the problem statement when it is dragged', () => {
            const panel = fixture.nativeElement.querySelector('.expanded') as HTMLElement;
            panel.getBoundingClientRect = () => ({ width: 400, height: 300, left: 600, top: 0, right: 1000, bottom: 300, x: 600, y: 0, toJSON: () => ({}) }) as DOMRect;

            pointer(divider()!, 'pointerdown', 600, 100);
            pointer(panel, 'pointermove', 500, 100);

            expect(panel.style.width).toBe('500px');

            pointer(panel, 'pointerup', 500, 100);
            expect(panel.classList.contains('card-resizable')).toBe(false);
        });

        it('keeps the panel within its minimum width', () => {
            const panel = fixture.nativeElement.querySelector('.expanded') as HTMLElement;
            panel.getBoundingClientRect = () => ({ width: 400, height: 300, left: 600, top: 0, right: 1000, bottom: 300, x: 600, y: 0, toJSON: () => ({}) }) as DOMRect;

            pointer(divider()!, 'pointerdown', 600, 100);
            pointer(panel, 'pointermove', 1500, 100);

            expect(panel.style.width).toBe('215px');
            pointer(panel, 'pointerup', 1500, 100);
        });

        it('is not rendered while the problem statement is collapsed', () => {
            fixture.componentRef.setInput('collapsed', true);
            fixture.detectChanges();

            expect(divider()).toBeNull();
            const collapsed = fixture.nativeElement.querySelector('.collapsed') as HTMLElement;
            expect(collapsed.getAttribute('aria-expanded')).toBe('false');
            // the layout contract finds the collapsed tab by it, to measure its distance to the solution
            expect(collapsed.getAttribute('data-testid')).toBe('resizeable-container-collapsed');
        });

        it('is not rendered without a right panel', () => {
            fixture.componentRef.setInput('examTimeline', true);
            fixture.detectChanges();

            expect(divider()).toBeNull();
            expect(fixture.nativeElement.querySelector('.expanded')).toBeNull();
        });
    });
});
