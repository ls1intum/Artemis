import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { NgbTooltipModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('Feature Overlay Component Tests', () => {
    let fixture: ComponentFixture<FeatureOverlayComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FeatureOverlayComponent);
            })
            .catch((error) => {
                console.log(error);
            });
    });

    it('should display tooltip and add "disabled" class when enabled is false', () => {
        fixture.componentRef.setInput('enabled', false);
        fixture.detectChanges();

        const tooltipDebugEl = fixture.debugElement.query(By.directive(NgbTooltip));
        expect(tooltipDebugEl).not.toBeNull();

        const tooltipInstance = tooltipDebugEl.injector.get(NgbTooltip);
        const expectedTooltip = 'artemisApp.featureToggles.title';
        expect(tooltipInstance.ngbTooltip).toEqual(expectedTooltip);

        const innerDivDebugEl = fixture.debugElement.query(By.css('div.disabled'));
        expect(innerDivDebugEl).not.toBeNull();
    });

    it('should not display tooltip nor add "disabled" class when enabled is true', () => {
        fixture.componentRef.setInput('enabled', true);
        fixture.detectChanges();

        const tooltipDebugEl = fixture.debugElement.query(By.directive(NgbTooltip));
        expect(tooltipDebugEl).not.toBeNull();

        const tooltipInstance = tooltipDebugEl.injector.get(NgbTooltip);
        expect(tooltipInstance.ngbTooltip).toEqual('');

        const innerDivDebugEl = fixture.debugElement.query(By.css('div.disabled'));
        expect(innerDivDebugEl).toBeNull();
    });
});
