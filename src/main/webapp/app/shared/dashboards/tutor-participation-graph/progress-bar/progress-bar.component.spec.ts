import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';

describe('ProgressBarComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgressBarComponent>;
    let component: ProgressBarComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgressBarComponent],
            providers: [{ provide: ThemeService, useClass: MockThemeService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgressBarComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([
        { percentage: 49, clazz: 'bg-danger' },
        { percentage: 99, clazz: 'bg-warning' },
        { percentage: 100, clazz: 'bg-success' },
    ])('uses correct background for percentage $percentage', ({ percentage, clazz }) => {
        fixture.componentRef.setInput('percentage', percentage);
        fixture.componentRef.setInput('numerator', percentage);
        fixture.componentRef.setInput('denominator', 100);
        expect(component.backgroundColorClass()).toBe(clazz);
    });

    it('updates foreground color correctly based on theme and percentage', () => {
        const themeService = TestBed.inject(ThemeService);
        fixture.componentRef.setInput('numerator', 100);
        fixture.componentRef.setInput('denominator', 100);

        fixture.componentRef.setInput('percentage', 100);
        expect(component.foregroundColorClass()).toBe('text-white');

        fixture.componentRef.setInput('percentage', 50);
        expect(component.foregroundColorClass()).toBe('text-dark');

        themeService.applyThemePreference(Theme.DARK);
        expect(component.foregroundColorClass()).toBe('text-white');
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
