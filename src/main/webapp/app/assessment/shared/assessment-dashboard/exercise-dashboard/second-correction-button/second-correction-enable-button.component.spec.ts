import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { SecondCorrectionEnableButtonComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/second-correction-button/second-correction-enable-button.component';

describe('SecondCorrectionEnableButtonComponent', () => {
    let comp: SecondCorrectionEnableButtonComponent;
    let fixture: ComponentFixture<SecondCorrectionEnableButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(SecondCorrectionEnableButtonComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('test call', () => {
        const emitStub = vi.spyOn(comp.ngModelChange, 'emit');
        comp.triggerSecondCorrectionButton();
        expect(emitStub).toHaveBeenCalledTimes(1);
    });

    it('should display the current state with switch semantics', async () => {
        fixture.componentRef.setInput('secondCorrectionEnabled', true);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const host: HTMLElement = fixture.nativeElement;
        const toggle = host.querySelector('input[role="switch"]') as HTMLInputElement;

        expect(host.textContent).toContain('artemisApp.exerciseAssessmentDashboard.enabled');
        expect(toggle.checked).toBe(true);
        expect(toggle.getAttribute('aria-label')).toBe('artemisApp.exerciseAssessmentDashboard.secondCorrection');
        expect(host.classList).toContain('inline-flex');
        expect(host.classList).toContain('align-middle');
    });
});
