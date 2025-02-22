import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { NgbTooltipModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';

describe('Feature Overlay Component Tests', () => {
    let fixture: ComponentFixture<FeatureOverlayComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FeatureOverlayComponent);
            });
    });

    it('should display tooltip and add "disabled" class when enabled is false', () => {
        fixture.componentRef.setInput('enabled', false);
        fixture.detectChanges();

        const tooltipDebugEl = fixture.debugElement.query(By.directive(NgbTooltip));
        expect(tooltipDebugEl).not.toBeNull();

        const tooltipInstance = tooltipDebugEl.injector.get(NgbTooltip);
        const expectedTooltip = 'This feature is not enabled. Ask your Artemis administrator or consult the documentation for more information.';
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
