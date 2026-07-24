import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { TumUiProgressBarComponent } from 'app/shared-ui/tum-ui/progress-bar/tum-ui-progress-bar.component';

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
        expect(host.className).toContain('tum-ui-progress-bar');
    });

    it('reflects the value into aria-valuenow and the fill width', () => {
        fixture.componentRef.setInput('value', 42);
        fixture.detectChanges();
        expect(host.getAttribute('aria-valuenow')).toBe('42');
        expect(fill().style.width).toBe('42%');
    });

    it('renders the default {value}{unit} label when showValue is true (default)', () => {
        fixture.componentRef.setInput('value', 60);
        fixture.detectChanges();
        expect(label().textContent?.trim()).toBe('60%');
    });

    it('respects a custom unit', () => {
        fixture.componentRef.setInput('value', 7);
        fixture.componentRef.setInput('unit', ' pts');
        fixture.detectChanges();
        expect(label().textContent?.trim()).toBe('7 pts');
    });

    it('hides the default label when showValue is false', () => {
        fixture.componentRef.setInput('value', 60);
        fixture.componentRef.setInput('showValue', false);
        fixture.detectChanges();
        expect(label().textContent?.trim()).toBe('');
    });

    it('does not render the default label for a zero value', () => {
        fixture.componentRef.setInput('value', 0);
        fixture.detectChanges();
        expect(label().textContent?.trim()).toBe('');
    });

    it('overrides the fill background via the color input', () => {
        fixture.componentRef.setInput('color', 'var(--success)');
        fixture.detectChanges();
        expect(fill().style.background).toBe('var(--success)');
    });

    it('forwards styleClass onto the bar', () => {
        fixture.componentRef.setInput('styleClass', 'mb-2 w-full');
        fixture.detectChanges();
        expect(host.className).toContain('mb-2');
        expect(host.className).toContain('w-full');
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
    it('renders projected label content even when showValue is false (parity with p-progressbar #content)', async () => {
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
