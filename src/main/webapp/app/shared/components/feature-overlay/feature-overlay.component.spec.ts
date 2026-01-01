import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { NgbTooltip, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Feature Overlay Component Tests', () => {
    let fixture: ComponentFixture<FeatureOverlayComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NgbTooltipModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(FeatureOverlayComponent);
    });

    it('should display tooltip and add "disabled" class when enabled is false', () => {
        fixture.componentRef.setInput('enabled', false);
        fixture.detectChanges();

        const tooltipDebugEl = fixture.debugElement.query(By.directive(NgbTooltip));
        expect(tooltipDebugEl).not.toBeNull();

        const tooltipInstance = tooltipDebugEl.injector.get(NgbTooltip);
        const expectedTooltip = 'featureOverview.overlay.title';
        expect(tooltipInstance.ngbTooltip).toEqual(expectedTooltip);

        const peElement = fixture.debugElement.query(By.css('div.pe-none'));
        expect(peElement).not.toBeNull();
        const opacityElement = fixture.debugElement.query(By.css('div.opacity-50'));
        expect(opacityElement).not.toBeNull();
    });

    it('should not display tooltip nor add "disabled" class when enabled is true', () => {
        fixture.componentRef.setInput('enabled', true);
        fixture.detectChanges();

        const tooltipDebugEl = fixture.debugElement.query(By.directive(NgbTooltip));
        expect(tooltipDebugEl).not.toBeNull();

        const tooltipInstance = tooltipDebugEl.injector.get(NgbTooltip);
        expect(tooltipInstance.ngbTooltip).toBe('');

        const peElement = fixture.debugElement.query(By.css('div.pe-none'));
        expect(peElement).toBeNull();
        const opacityElement = fixture.debugElement.query(By.css('div.opacity-50'));
        expect(opacityElement).toBeNull();
    });
});
