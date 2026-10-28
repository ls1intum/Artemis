import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumAetUiSplitterComponent, TumAetUiSplitterPanelDirective } from './tumaet-ui-splitter.component';

@Component({
    imports: [TumAetUiSplitterComponent, TumAetUiSplitterPanelDirective],
    template: `<tumaet-ui-splitter
        [sizes]="sizes()"
        [minSizes]="[20, 30]"
        [orientation]="orientation()"
        [collapsedSecondSize]="collapsed() ? '3rem' : undefined"
        separatorLabel="Resize documents"
        (sizesChange)="changes.push($event)"
        (resizeEnd)="ends.push($event)"
    >
        <ng-template tumAetUiSplitterPanel><input aria-label="Draft" value="Unsaved draft" /></ng-template>
        <ng-template tumAetUiSplitterPanel><iframe title="Document"></iframe></ng-template>
    </tumaet-ui-splitter>`,
})
class SplitterHostComponent {
    readonly sizes = signal([60, 40]);
    readonly orientation = signal<'horizontal' | 'vertical'>('horizontal');
    readonly collapsed = signal(false);
    readonly changes: number[][] = [];
    readonly ends: number[][] = [];
}

describe('TumAetUiSplitterComponent', () => {
    let fixture: ComponentFixture<SplitterHostComponent>;
    let host: SplitterHostComponent;
    let separator: HTMLElement;
    const key = (value: string) => {
        separator.dispatchEvent(new KeyboardEvent('keydown', { key: value, bubbles: true, cancelable: true }));
        fixture.detectChanges();
    };
    const pointer = (target: EventTarget, type: string, x = 0, y = 0) => {
        const event = new Event(type, { bubbles: true, cancelable: true });
        Object.defineProperties(event, { pointerId: { value: 1 }, button: { value: 0 }, clientX: { value: x }, clientY: { value: y } });
        target.dispatchEvent(event);
        fixture.detectChanges();
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [SplitterHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(SplitterHostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
        separator = fixture.nativeElement.querySelector('[role="separator"]');
        vi.spyOn(fixture.debugElement.query(By.directive(TumAetUiSplitterComponent)).nativeElement, 'getBoundingClientRect').mockReturnValue({
            left: 0,
            top: 0,
            width: 1008,
            height: 508,
        } as DOMRect);
    });

    it('renders both templates with a named focusable separator and controlled values', () => {
        expect(fixture.nativeElement.querySelector('input').value).toBe('Unsaved draft');
        expect(fixture.nativeElement.querySelector('iframe').title).toBe('Document');
        expect(separator.getAttribute('aria-label')).toBe('Resize documents');
        expect(separator.tabIndex).toBe(0);
        expect(separator.getAttribute('aria-valuenow')).toBe('60');
        host.sizes.set([10, 90]);
        fixture.detectChanges();
        expect(separator.getAttribute('aria-valuenow')).toBe('20');
    });

    it('resizes with axis arrows, Home and End and clamps both minimums', () => {
        key('ArrowRight');
        expect(host.ends.at(-1)).toEqual([61, 39]);
        key('ArrowLeft');
        expect(host.ends.at(-1)).toEqual([60, 40]);
        key('Home');
        expect(host.ends.at(-1)).toEqual([20, 80]);
        key('End');
        expect(host.ends.at(-1)).toEqual([70, 30]);
        key('ArrowRight');
        expect(separator.getAttribute('aria-valuenow')).toBe('70');
        host.orientation.set('vertical');
        fixture.detectChanges();
        expect(separator.getAttribute('aria-orientation')).toBe('horizontal');
        key('ArrowUp');
        expect(host.ends.at(-1)).toEqual([69, 31]);
        key('ArrowDown');
        expect(host.ends.at(-1)).toEqual([70, 30]);
    });

    it('keeps projected identity when collapsed and expanded', () => {
        const input = fixture.nativeElement.querySelector('input');
        input.value = 'Edited';
        host.collapsed.set(true);
        fixture.detectChanges();
        expect(separator.hidden).toBe(true);
        host.collapsed.set(false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('input')).toBe(input);
        expect(input.value).toBe('Edited');
    });

    it('drags across embedded frames, emits sizes and removes the drag shield on commit', () => {
        pointer(separator, 'pointerdown', 600);
        expect(document.querySelector('[data-tumaet-splitter-shield]')).not.toBeNull();
        pointer(document, 'pointermove', 650);
        expect(host.changes.at(-1)).toEqual([65, 35]);
        pointer(document, 'pointerup', 650);
        expect(host.ends.at(-1)).toEqual([65, 35]);
        expect(document.querySelector('[data-tumaet-splitter-shield]')).toBeNull();
        pointer(document, 'pointermove', 100);
        expect(host.changes).toHaveLength(1);
    });

    it.each(['pointercancel', 'blur'])('cleans up cancelled gestures on %s without committing', (event) => {
        pointer(separator, 'pointerdown', 600);
        pointer(document, 'pointermove', 650);
        pointer(event === 'blur' ? window : document, event);
        expect(document.querySelector('[data-tumaet-splitter-shield]')).toBeNull();
        expect(host.ends).toEqual([]);
        expect(separator.getAttribute('aria-valuenow')).toBe('60');
    });

    it('removes shield and listeners on destroy', () => {
        pointer(separator, 'pointerdown', 600);
        fixture.destroy();
        expect(document.querySelector('[data-tumaet-splitter-shield]')).toBeNull();
        document.dispatchEvent(new Event('pointermove'));
        expect(host.changes).toEqual([]);
    });
});
