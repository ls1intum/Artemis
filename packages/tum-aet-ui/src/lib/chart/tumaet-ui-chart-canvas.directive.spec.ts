import { inputBinding, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TumAetUiChartCanvasDirective } from './tumaet-ui-chart-canvas.directive';

describe('TumAetUiChartCanvasDirective', () => {
    it('exposes a named graphic and treats an unnamed chart as decorative', () => {
        const label = signal<string | undefined>(undefined);
        const labelledBy = signal<string | undefined>(undefined);
        const fixture = TestBed.createDirective(TumAetUiChartCanvasDirective, {
            bindings: [inputBinding('ariaLabel', label), inputBinding('ariaLabelledBy', labelledBy)],
        });
        fixture.detectChanges();
        const canvas = fixture.nativeElement;
        expect(canvas.getAttribute('role')).toBe('img');
        expect(canvas.getAttribute('aria-hidden')).toBe('true');
        label.set('Submissions');
        fixture.detectChanges();
        expect(canvas.getAttribute('aria-label')).toBe('Submissions');
        expect(canvas.hasAttribute('aria-hidden')).toBe(false);
        label.set(undefined);
        labelledBy.set('heading');
        fixture.detectChanges();
        expect(canvas.hasAttribute('aria-label')).toBe(false);
        expect(canvas.getAttribute('aria-labelledby')).toBe('heading');
        expect(canvas.hasAttribute('aria-hidden')).toBe(false);
        labelledBy.set(undefined);
        fixture.detectChanges();
        expect(canvas.getAttribute('aria-hidden')).toBe('true');
    });
});
