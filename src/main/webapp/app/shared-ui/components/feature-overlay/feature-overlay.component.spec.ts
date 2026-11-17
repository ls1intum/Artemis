import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeatureOverlayComponent } from 'app/shared-ui/components/feature-overlay/feature-overlay.component';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Feature Overlay Component Tests', () => {
    let fixture: ComponentFixture<FeatureOverlayComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FeatureOverlayComponent);
            });
    });

    it('should display tooltip and disable the content when enabled is false', () => {
        fixture.componentRef.setInput('enabled', false);
        fixture.detectChanges();

        const tooltipDebugEl = fixture.debugElement.query(By.directive(TumAetUiTooltipDirective));
        expect(tooltipDebugEl).not.toBeNull();

        const tooltipInstance = tooltipDebugEl.injector.get(TumAetUiTooltipDirective);
        expect(tooltipInstance.content()).toBe('featureOverview.overlay.title');

        const content = fixture.debugElement.query(By.css('[data-testid="feature-overlay-content"]')).nativeElement as HTMLElement;
        expect(content.classList).toContain('pointer-events-none');
        expect(content.classList).toContain('opacity-50');
    });

    it('should not display tooltip nor disable the content when enabled is true', () => {
        fixture.componentRef.setInput('enabled', true);
        fixture.detectChanges();

        const tooltipDebugEl = fixture.debugElement.query(By.directive(TumAetUiTooltipDirective));
        expect(tooltipDebugEl).not.toBeNull();

        const tooltipInstance = tooltipDebugEl.injector.get(TumAetUiTooltipDirective);
        expect(tooltipInstance.content()).toBeUndefined();

        const content = fixture.debugElement.query(By.css('[data-testid="feature-overlay-content"]')).nativeElement as HTMLElement;
        expect(content.classList).not.toContain('pointer-events-none');
        expect(content.classList).not.toContain('opacity-50');
    });
});
