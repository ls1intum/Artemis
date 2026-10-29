import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumAetUiSplitterComponent } from './tumaet-ui-splitter.component';
import { TUM_AET_UI_TRANSLATOR } from '../i18n/tumaet-ui-translations';

@Component({
    imports: [TumAetUiSplitterComponent],
    template: `<tumaet-ui-splitter
        [panelSizes]="sizes()"
        [minSizes]="minimums()"
        [gutterSize]="gutter()"
        [layout]="orientation()"
        [collapsedSecondSize]="collapsed() ? '3rem' : undefined"
        [ariaLabel]="label()"
        (sizesChange)="changes.push($event)"
        (resizeEnd)="ends.push($event)"
    >
        <ng-template #panel><input aria-label="Draft" value="Unsaved draft" /></ng-template>
        <ng-template #panel><iframe title="Document"></iframe></ng-template>
    </tumaet-ui-splitter>`,
})
class PersistentSplitterHostComponent {
    readonly sizes = signal([60, 40]);
    readonly minimums = signal([20, 30]);
    readonly gutter = signal(8);
    readonly label = signal<string | undefined>('Resize documents');
    readonly orientation = signal<'horizontal' | 'vertical'>('horizontal');
    readonly collapsed = signal(false);
    readonly changes: number[][] = [];
    readonly ends: number[][] = [];
}

describe('TumAetUiSplitterComponent persistent panels and gesture cleanup', () => {
    let fixture: ComponentFixture<PersistentSplitterHostComponent>;
    let host: PersistentSplitterHostComponent;
    let separator: HTMLElement;
    let translatedLabel: ReturnType<typeof signal<string>>;
    const key = (value: string) => {
        separator.dispatchEvent(new KeyboardEvent('keydown', { key: value, bubbles: true, cancelable: true }));
        fixture.detectChanges();
    };
    const pointer = (target: EventTarget, type: string, x = 0, y = 0, pointerId = 1) => {
        const event = new Event(type, { bubbles: true, cancelable: true });
        Object.defineProperties(event, { pointerId: { value: pointerId }, button: { value: 0 }, clientX: { value: x }, clientY: { value: y } });
        target.dispatchEvent(event);
        fixture.detectChanges();
    };

    beforeEach(async () => {
        translatedLabel = signal('Resize panels');
        await TestBed.configureTestingModule({
            imports: [PersistentSplitterHostComponent],
            providers: [{ provide: TUM_AET_UI_TRANSLATOR, useValue: { translationChanges: translatedLabel, translate: () => translatedLabel() } }],
        }).compileComponents();
        fixture = TestBed.createComponent(PersistentSplitterHostComponent);
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

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
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
        expect(host.ends.at(-1)).toEqual([62, 38]);
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
        expect(host.ends.at(-1)).toEqual([68, 32]);
        key('ArrowDown');
        expect(host.ends.at(-1)).toEqual([70, 30]);
    });

    it('normalizes input weights and ignores invalid size arrays', () => {
        host.sizes.set([6, 4]);
        fixture.detectChanges();
        expect(separator.getAttribute('aria-valuenow')).toBe('60');
        host.sizes.set([Number.NaN, 40]);
        fixture.detectChanges();
        expect(separator.getAttribute('aria-valuenow')).toBe('50');
    });

    it('normalizes impossible minimums and handles nonfinite constraints', () => {
        host.minimums.set([80, 80]);
        fixture.detectChanges();
        expect(separator.getAttribute('aria-valuemin')).toBe('50');
        expect(separator.getAttribute('aria-valuemax')).toBe('50');
        expect(separator.getAttribute('aria-valuenow')).toBe('50');
        host.minimums.set([Number.NaN, Number.POSITIVE_INFINITY]);
        fixture.detectChanges();
        expect(separator.getAttribute('aria-valuemin')).toBe('0');
        expect(separator.getAttribute('aria-valuemax')).toBe('100');
    });

    it('updates the translated label when the catalog changes and allows an explicit label', () => {
        host.label.set(undefined);
        fixture.detectChanges();
        expect(separator.getAttribute('aria-label')).toBe('Resize panels');
        translatedLabel.set('Bereiche skalieren');
        fixture.detectChanges();
        expect(separator.getAttribute('aria-label')).toBe('Bereiche skalieren');
        host.label.set('Resize documents');
        fixture.detectChanges();
        expect(separator.getAttribute('aria-label')).toBe('Resize documents');
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

    it.each(['pointercancel', 'lostpointercapture', 'blur'])('cleans up cancelled gestures on %s without committing', (event) => {
        pointer(separator, 'pointerdown', 600);
        pointer(document, 'pointermove', 650);
        pointer(event === 'blur' ? window : event === 'lostpointercapture' ? separator : document, event);
        expect(document.querySelector('[data-tumaet-splitter-shield]')).toBeNull();
        expect(host.ends).toEqual([]);
        expect(separator.getAttribute('aria-valuenow')).toBe('60');
    });

    it('uses the rendered gutter size when calculating pointer percentages', () => {
        vi.spyOn(separator, 'getBoundingClientRect').mockReturnValue({ width: 108, height: 508 } as DOMRect);
        pointer(separator, 'pointerdown', 600);
        pointer(document, 'pointermove', 645);
        expect(host.changes.at(-1)).toEqual([65, 35]);
        pointer(document, 'pointerup', 645);
        expect(host.ends.at(-1)).toEqual([65, 35]);
    });

    it('ignores other pointers and rolls back cancellation without a completion event', () => {
        pointer(separator, 'pointerdown', 600);
        pointer(document, 'pointermove', 650, 0, 2);
        pointer(document, 'pointercancel', 0, 0, 2);
        expect(host.changes).toEqual([]);
        expect(document.querySelector('[data-tumaet-splitter-shield]')).not.toBeNull();
        pointer(document, 'pointermove', 650);
        pointer(document, 'pointercancel');
        pointer(document, 'pointerup', 650);
        expect(host.ends).toEqual([]);
        expect(separator.getAttribute('aria-valuenow')).toBe('60');
    });

    it('cleans up gesture protection on collapse and controlled layout changes', () => {
        pointer(separator, 'pointerdown', 600);
        host.collapsed.set(true);
        fixture.detectChanges();
        expect(document.querySelector('[data-tumaet-splitter-shield]')).toBeNull();
        host.collapsed.set(false);
        fixture.detectChanges();
        pointer(separator, 'pointerdown', 600);
        host.orientation.set('vertical');
        fixture.detectChanges();
        expect(document.querySelector('[data-tumaet-splitter-shield]')).toBeNull();
        expect(host.ends).toEqual([]);
    });

    it('removes shield and listeners on destroy', () => {
        pointer(separator, 'pointerdown', 600);
        fixture.destroy();
        expect(document.querySelector('[data-tumaet-splitter-shield]')).toBeNull();
        document.dispatchEvent(new Event('pointermove'));
        expect(host.changes).toEqual([]);
    });
});

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
    it('keeps resizing available when local storage reads and writes fail', () => {
        fixture.destroy();
        const get = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
            throw new Error('Storage disabled');
        });
        const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new Error('Storage disabled');
        });
        try {
            fixture = TestBed.createComponent(SplitterHostComponent);
            fixture.detectChanges();
            key('ArrowRight');
            expect(fixture.componentInstance.sizes).toEqual([42, 58]);
        } finally {
            get.mockRestore();
            set.mockRestore();
        }
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
