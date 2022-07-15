import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { ArtemisTestModule } from '../../test.module';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective } from 'ng-mocks';
import { SimpleChange } from '@angular/core';

describe('ProgressBarComponent', () => {
    let fixture: ComponentFixture<ProgressBarComponent>;
    let component: ProgressBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgressBarComponent, MockDirective(NgbTooltip)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgressBarComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([
        { percentage: 49, clazz: 'bg-danger' },
        { percentage: 99, clazz: 'bg-warning' },
        { percentage: 100, clazz: 'bg-success' },
    ])('uses correct background', ({ percentage, clazz }) => {
        component.percentage = percentage;
        fixture.detectChanges();
        component.ngOnChanges({ percentage: {} as SimpleChange });
        expect(component.backgroundColorClass).toBe(clazz);
    });

    it('updates foreground color correctly based on theme and percentage', () => {
        const themeService = TestBed.inject(ThemeService);
        fixture.detectChanges();

        component.percentage = 100;
        component.ngOnChanges({ percentage: {} as SimpleChange });
        expect(component.foregroundColorClass).toBe('text-white');

        component.percentage = 50;
        component.ngOnChanges({ percentage: {} as SimpleChange });
        expect(component.foregroundColorClass).toBe('text-dark');

        themeService.applyThemeExplicitly(Theme.DARK);
        expect(component.foregroundColorClass).toBe('text-white');
    });

    it('unsubscribes from theme service on destroy', () => {
        fixture.detectChanges();
        expect(component.themeSubscription).not.toBeUndefined();
        expect(component.themeSubscription.closed).toBeFalse();

        component.ngOnDestroy();

        expect(component.themeSubscription.closed).toBeTrue();
    });
});
