import { By } from '@angular/platform-browser';
import { TumAetUiProgressBarComponent } from '@tumaet/ui-angular';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgressBarComponent } from 'app/exercise/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';

describe('ProgressBarComponent', () => {
    let fixture: ComponentFixture<ProgressBarComponent>;
    let component: ProgressBarComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgressBarComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgressBarComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([
        { percentage: 49, severity: 'danger' },
        { percentage: 99, severity: 'warn' },
        { percentage: 100, severity: 'success' },
    ])('uses correct background for percentage $percentage', ({ percentage, severity }) => {
        fixture.componentRef.setInput('percentage', percentage);
        fixture.componentRef.setInput('numerator', percentage);
        fixture.componentRef.setInput('denominator', 100);
        fixture.detectChanges();
        const bar = fixture.debugElement.query(By.directive(TumAetUiProgressBarComponent)).componentInstance as TumAetUiProgressBarComponent;
        expect(bar.severity()).toBe(severity);
        expect(fixture.nativeElement.textContent).toContain(`${percentage} / 100 (${percentage}%)`);
    });

    it('returns 0 percentage when both numerator and denominator are zero', () => {
        fixture.componentRef.setInput('percentage', 42);
        fixture.componentRef.setInput('numerator', 0);
        fixture.componentRef.setInput('denominator', 0);
        expect(component.normalizedPercentage()).toBe(0);
    });

    it('returns 0 percentage when value is NaN', () => {
        fixture.componentRef.setInput('percentage', NaN);
        fixture.componentRef.setInput('numerator', 1);
        fixture.componentRef.setInput('denominator', 2);
        expect(component.normalizedPercentage()).toBe(0);
    });
});
