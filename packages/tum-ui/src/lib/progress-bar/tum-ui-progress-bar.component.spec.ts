import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { TumUiProgressBarComponent } from './tum-ui-progress-bar.component';

describe('TumUiProgressBarComponent', () => {
    let fixture: ComponentFixture<TumUiProgressBarComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiProgressBarComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiProgressBarComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function fill(): HTMLElement {
        return fixture.debugElement.query(By.css('.tum-ui-progress-bar-value')).nativeElement;
    }

    function label(): HTMLElement {
        return fixture.debugElement.query(By.css('.tum-ui-progress-bar-label')).nativeElement;
    }

    it('exposes progressbar semantics with min/max and a defaulted value', () => {
        expect(host.getAttribute('role')).toBe('progressbar');
        expect(host.getAttribute('aria-valuemin')).toBe('0');
        expect(host.getAttribute('aria-valuemax')).toBe('100');
        expect(host.getAttribute('aria-valuenow')).toBe('0');
    });

    it('reflects the value into aria-valuenow and the fill width', () => {
        fixture.componentRef.setInput('value', 42);
        fixture.detectChanges();
        expect(host.getAttribute('aria-valuenow')).toBe('42');
        expect(fill().style.width).toBe('42%');
    });

    it('exposes an accessible name', () => {
        fixture.componentRef.setInput('ariaLabel', 'Course completion');
        fixture.detectChanges();
        expect(host.getAttribute('aria-label')).toBe('Course completion');
    });

    it('clamps the exposed value and the fill to the declared range', () => {
        fixture.componentRef.setInput('value', 130);
        fixture.detectChanges();
        expect(host.getAttribute('aria-valuenow')).toBe('100');
        expect(fill().style.width).toBe('100%');

        fixture.componentRef.setInput('value', -10);
        fixture.detectChanges();
        expect(host.getAttribute('aria-valuenow')).toBe('0');
        expect(fill().style.width).toBe('0%');
    });

    it('reports a real scale, so "17 of 42" does not reach assistive technology as 40 percent', () => {
        fixture.componentRef.setInput('min', 0);
        fixture.componentRef.setInput('max', 42);
        fixture.componentRef.setInput('value', 17);
        fixture.componentRef.setInput('valueText', '17 of 42 files');
        fixture.detectChanges();

        expect(host.getAttribute('aria-valuemin')).toBe('0');
        expect(host.getAttribute('aria-valuemax')).toBe('42');
        expect(host.getAttribute('aria-valuenow')).toBe('17');
        expect(host.getAttribute('aria-valuetext')).toBe('17 of 42 files');
        expect(fill().style.width).toBe(`${(17 / 42) * 100}%`);
    });

    it('offsets the fill against a non-zero floor', () => {
        fixture.componentRef.setInput('min', 10);
        fixture.componentRef.setInput('max', 20);
        fixture.componentRef.setInput('value', 15);
        fixture.detectChanges();
        expect(fill().style.width).toBe('50%');
    });

    it('reports zero rather than dividing by a scale of no width', () => {
        fixture.componentRef.setInput('min', 5);
        fixture.componentRef.setInput('max', 5);
        fixture.componentRef.setInput('value', 5);
        fixture.detectChanges();
        expect(fill().style.width).toBe('0%');
    });

    it('renders the consumer-supplied reading beside the bar, never inside the clipping fill', () => {
        fixture.componentRef.setInput('valueText', '60%');
        fixture.detectChanges();
        expect(label().textContent?.trim()).toBe('60%');
        expect(fill().contains(label())).toBe(false);
    });

    it('hides the reading when showValue is off', () => {
        fixture.componentRef.setInput('valueText', '60%');
        fixture.componentRef.setInput('showValue', false);
        fixture.detectChanges();
        expect(label().textContent?.trim()).toBe('');
    });

    it('withholds the transition until the first value has been painted', async () => {
        // A page load that sweeps the bar up from zero implies progress the reader did not witness, so the flag
        // that enables the transition is only set once the initial width has been drawn.
        const fresh = TestBed.createComponent(TumUiProgressBarComponent);
        const freshHost = fresh.nativeElement as HTMLElement;
        expect(freshHost.getAttribute('data-committed')).toBeNull();

        fresh.detectChanges();
        await fresh.whenStable();
        fresh.detectChanges();
        expect(freshHost.getAttribute('data-committed')).toBe('true');
    });
});

@Component({
    template: `<tum-ui-progress-bar [value]="30" [showValue]="false"
        ><span class="projected">{{ 30 }}%</span></tum-ui-progress-bar
    >`,
    imports: [TumUiProgressBarComponent],
})
class ProgressBarHostComponent {}

describe('TumUiProgressBarComponent (content projection)', () => {
    it('renders projected content when the default value label is hidden', async () => {
        await TestBed.configureTestingModule({
            imports: [ProgressBarHostComponent],
        }).compileComponents();
        const fixture = TestBed.createComponent(ProgressBarHostComponent);
        fixture.detectChanges();
        const projected = fixture.debugElement.query(By.css('.projected'));
        expect(projected).not.toBeNull();
        expect(projected.nativeElement.textContent.trim()).toBe('30%');
    });
});
