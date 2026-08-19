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

    it('clamps the exposed value, fill, and label to the valid range', () => {
        fixture.componentRef.setInput('value', 130);
        fixture.detectChanges();
        expect(host.getAttribute('aria-valuenow')).toBe('100');
        expect(fill().style.width).toBe('100%');
        expect(label().textContent?.trim()).toBe('100%');

        fixture.componentRef.setInput('value', -10);
        fixture.detectChanges();
        expect(host.getAttribute('aria-valuenow')).toBe('0');
        expect(fill().style.width).toBe('0%');
        expect(label().textContent?.trim()).toBe('');
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

    it('reflects the size into a data attribute so the stylesheet can pick the slim rail', () => {
        expect(host.getAttribute('data-size')).toBe('default');

        fixture.componentRef.setInput('size', 'small');
        fixture.detectChanges();
        expect(host.getAttribute('data-size')).toBe('small');
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
