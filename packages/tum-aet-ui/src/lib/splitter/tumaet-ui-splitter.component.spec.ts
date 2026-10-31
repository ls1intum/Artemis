import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumAetUiSplitterComponent } from './tumaet-ui-splitter.component';

@Component({
    imports: [TumAetUiSplitterComponent],
    template: `<tumaet-ui-splitter [layout]="layout()" [panelSizes]="[40, 60]" [minSizes]="[20, 30]" stateKey="splitter-test" (resizeEnd)="sizes = $event">
        <ng-template #panel><button type="button">Canvas</button></ng-template>
        <ng-template #panel><p>Instructions</p></ng-template>
    </tumaet-ui-splitter>`,
})
class SplitterHostComponent {
    readonly layout = signal<'horizontal' | 'vertical'>('horizontal');
    sizes: number[] = [];
}

describe('TumAetUiSplitterComponent', () => {
    let fixture: ComponentFixture<SplitterHostComponent>;
    const separator = (): HTMLElement => fixture.nativeElement.querySelector('[role="separator"]');
    const key = (value: string, shiftKey = false): void => {
        separator().dispatchEvent(new KeyboardEvent('keydown', { key: value, shiftKey, bubbles: true }));
        fixture.detectChanges();
    };

    beforeEach(async () => {
        localStorage.removeItem('splitter-test-horizontal');
        localStorage.removeItem('splitter-test-vertical');
        await TestBed.configureTestingModule({ imports: [SplitterHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(SplitterHostComponent);
        fixture.detectChanges();
    });

    it('projects two independently interactive panels and exposes an accessible resize separator', () => {
        expect(fixture.nativeElement.querySelector('button').textContent).toBe('Canvas');
        expect(fixture.nativeElement.querySelector('p').textContent).toBe('Instructions');
        expect(separator().getAttribute('aria-label')).toBe('Resize panels');
        expect(separator().getAttribute('aria-orientation')).toBe('vertical');
        expect(separator().getAttribute('aria-valuenow')).toBe('40');
        expect(separator().tabIndex).toBe(0);
    });

    it('resizes by keyboard within both panel limits and emits the final sizes', () => {
        key('ArrowRight');
        expect(fixture.componentInstance.sizes).toEqual([42, 58]);
        key('ArrowLeft', true);
        expect(fixture.componentInstance.sizes).toEqual([32, 68]);
        key('Home');
        key('ArrowLeft');
        expect(fixture.componentInstance.sizes).toEqual([20, 80]);
        key('End');
        key('ArrowRight');
        expect(fixture.componentInstance.sizes).toEqual([70, 30]);
        expect(localStorage.getItem('splitter-test-horizontal')).toBe('70');
    });

    it('uses vertical keys after changing orientation and retains independent stored sizes', () => {
        key('ArrowRight');
        fixture.componentInstance.layout.set('vertical');
        fixture.changeDetectorRef.markForCheck();
        fixture.detectChanges();
        expect(separator().getAttribute('aria-orientation')).toBe('horizontal');
        expect(separator().getAttribute('aria-valuenow')).toBe('40');
        key('ArrowDown');
        expect(fixture.componentInstance.sizes).toEqual([42, 58]);
        key('ArrowUp', true);
        expect(fixture.componentInstance.sizes).toEqual([32, 68]);
        fixture.componentInstance.layout.set('horizontal');
        fixture.changeDetectorRef.markForCheck();
        fixture.detectChanges();
        expect(separator().getAttribute('aria-valuenow')).toBe('42');
    });

    it('ignores unrelated keys and invalid stored values', () => {
        key('Enter');
        expect(fixture.componentInstance.sizes).toEqual([]);
        fixture.destroy();
        localStorage.setItem('splitter-test-horizontal', 'invalid');
        fixture = TestBed.createComponent(SplitterHostComponent);
        fixture.detectChanges();
        expect(separator().getAttribute('aria-valuenow')).toBe('40');
    });
    it('resizes with a captured pointer and clamps to both minimum panel sizes', () => {
        const handle = separator();
        handle.setPointerCapture = vi.fn();
        const host = fixture.nativeElement.querySelector('tumaet-ui-splitter') as HTMLElement;
        vi.spyOn(host, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 1000, 400));
        const pointer = (type: string, x: number, button = 0): void => {
            const event = new MouseEvent(type, { clientX: x, button, bubbles: true });
            Object.defineProperty(event, 'pointerId', { value: 1 });
            handle.dispatchEvent(event);
            fixture.detectChanges();
        };
        pointer('pointerdown', 400, 2);
        pointer('pointermove', 500);
        expect(handle.getAttribute('aria-valuenow')).toBe('40');
        pointer('pointerdown', 400);
        expect(handle.setPointerCapture).toHaveBeenCalledWith(1);
        pointer('pointermove', 900);
        expect(handle.getAttribute('aria-valuenow')).toBe('70');
        pointer('pointerup', 900);
        expect(fixture.componentInstance.sizes).toEqual([70, 30]);
        expect(localStorage.getItem('splitter-test-horizontal')).toBe('70');
    });
});
