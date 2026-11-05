import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';

import { PresentationResultTagComponent } from 'app/presentation/manage/presentation-result-tag.component';
import { PresentationAssessment, PresentationAssessmentInstance, PresentationGradingMethod } from 'app/presentation/shared/entities/presentation-assessment.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PresentationResultTagComponent', () => {
    let fixture: ComponentFixture<PresentationResultTagComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationResultTagComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(PresentationResultTagComponent);
    });

    function render(presentationAssessment: PresentationAssessment, instance: PresentationAssessmentInstance): HTMLElement {
        fixture.componentRef.setInput('presentationAssessment', presentationAssessment);
        fixture.componentRef.setInput('instance', instance);
        fixture.detectChanges();
        return fixture.nativeElement.querySelector('[data-testid="instance-result"]');
    }

    function severity(tag: HTMLElement): string | null {
        return tag.querySelector('[data-severity]')?.getAttribute('data-severity') ?? null;
    }

    it.each([
        { passed: true, key: 'passed', expectedSeverity: 'success' },
        { passed: false, key: 'failed', expectedSeverity: 'danger' },
    ])('should show a pass/fail result as $key', ({ passed, key, expectedSeverity }) => {
        const tag = render({ gradingMethod: PresentationGradingMethod.PASS_FAIL }, { passed });

        expect(tag.textContent).toContain(`artemisApp.presentationAssessment.result.${key}`);
        expect(severity(tag)).toBe(expectedSeverity);
    });

    it('should show a rubric result in percent', () => {
        const tag = render({ gradingMethod: PresentationGradingMethod.RUBRIC }, { rubricScore: 72.5 });

        expect(tag.textContent?.trim()).toBe('72.5 %');
        expect(severity(tag)).toBe('info');
    });

    it.each([PresentationGradingMethod.SCORE, undefined])('should show the points against the maximum for the method %s', (gradingMethod) => {
        const tag = render({ gradingMethod, maxPoints: 20 }, { resultPoints: 12 });

        expect(tag.textContent?.trim()).toBe('12 / 20');
    });
});
