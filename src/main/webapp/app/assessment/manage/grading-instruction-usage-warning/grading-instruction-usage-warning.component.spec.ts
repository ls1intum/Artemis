import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GradingInstructionUsageWarningComponent } from './grading-instruction-usage-warning.component';

describe('GradingInstructionUsageWarningComponent', () => {
    let fixture: ComponentFixture<GradingInstructionUsageWarningComponent>;
    const feedback = { gradingInstruction: { id: 1, usageCount: 1, credits: -1 } } as Feedback;
    const otherFeedback = { gradingInstruction: { id: 1, usageCount: 1, credits: -1 } } as Feedback;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(GradingInstructionUsageWarningComponent);
        fixture.componentRef.setInput('feedback', feedback);
    });

    it('shows the cap information on the first use too when the assessment exceeds the limit', () => {
        fixture.componentRef.setInput('allFeedbacks', [feedback, otherFeedback]);
        fixture.detectChanges();
        expect(fixture.componentInstance.usage()).toEqual({ count: 2, limit: 1 });
        expect(fixture.nativeElement.querySelector('[data-testid="grading-instruction-usage-warning"]')).toBeTruthy();
        expect(feedback.gradingInstruction?.credits).toBe(-1);
    });

    it.each([undefined, 0, -1])('does not warn about an unlimited instruction (%s)', (usageCount) => {
        const unlimitedFeedback = { gradingInstruction: { id: 1, usageCount } } as Feedback;
        fixture.componentRef.setInput('feedback', unlimitedFeedback);
        fixture.componentRef.setInput('allFeedbacks', [unlimitedFeedback, otherFeedback]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="grading-instruction-usage-warning"]')).toBeNull();
    });

    it('does not warn at the limit or count unrelated instructions', () => {
        fixture.componentRef.setInput('allFeedbacks', [feedback, { gradingInstruction: { id: 2 } } as Feedback, new Feedback()]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="grading-instruction-usage-warning"]')).toBeNull();
    });

    it('ignores automatic test feedback only in programming scoring context', () => {
        const automatic = { ...otherFeedback, type: FeedbackType.AUTOMATIC };
        fixture.componentRef.setInput('allFeedbacks', [feedback, automatic]);
        fixture.detectChanges();
        expect(fixture.componentInstance.usage()).toEqual({ count: 2, limit: 1 });
        fixture.componentRef.setInput('capAutomaticTestSubtotal', true);
        fixture.detectChanges();
        expect(fixture.componentInstance.usage()).toBeUndefined();
        fixture.componentRef.setInput('allFeedbacks', [feedback, { ...automatic, text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + 'issue' }]);
        fixture.detectChanges();
        expect(fixture.componentInstance.usage()).toEqual({ count: 2, limit: 1 });
    });

    it('does not show assessor instructions in read-only student feedback', () => {
        fixture.componentRef.setInput('allFeedbacks', [feedback, otherFeedback]);
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="grading-instruction-usage-warning"]')).toBeNull();
    });
});
