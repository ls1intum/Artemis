import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextBlockAssessmentCardComponent } from 'app/text/manage/assess/textblock-assessment-card/text-block-assessment-card.component';
import { TextBlockFeedbackEditorComponent } from 'app/text/manage/assess/textblock-feedback-editor/text-block-feedback-editor.component';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextBlockType } from 'app/text/shared/entities/text-block.model';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { EventEmitter } from '@angular/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/manage/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

/**
 * Test suite for TextBlockAssessmentCardComponent.
 * Tests component creation, selection behavior, autofocus functionality,
 * text block display, feedback editor integration, and assessment event tracking.
 */
describe('TextblockAssessmentCardComponent', () => {
    setupTestBed({ zoneless: true });
    let component: TextBlockAssessmentCardComponent;
    let fixture: ComponentFixture<TextBlockAssessmentCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), FaIconComponent],
            declarations: [
                TextBlockAssessmentCardComponent,
                TextBlockFeedbackEditorComponent,
                TranslatePipeMock,
                MockDirective(TranslateDirective),
                MockComponent(ConfirmIconComponent),
                MockComponent(GradingInstructionLinkIconComponent),
                MockComponent(AssessmentCorrectionRoundBadgeComponent),
                MockComponent(FaLayersComponent),
            ],
            providers: [
                MockProvider(StructuredGradingCriterionService),
                MockProvider(TextAssessmentAnalytics),
                MockProvider(TextAssessmentService),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TextBlockAssessmentCardComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('textBlockRef', TextBlockRef.new());
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('cannot be selected in readOnly mode', () => {
        fixture.componentRef.setInput('readOnly', true);
        component.didSelect = new EventEmitter();
        const selectSpy = vi.spyOn(component.didSelect, 'emit');
        component.select();
        expect(selectSpy).not.toHaveBeenCalled();
    });

    it('should autofocus', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('readOnly', false);
        const textBlockRef = TextBlockRef.new();
        textBlockRef.selectable = true;
        textBlockRef.feedback = {
            type: FeedbackType.MANUAL,
        };
        fixture.componentRef.setInput('textBlockRef', textBlockRef);
        fixture.componentRef.setInput('selected', false);
        component.feedbackEditor = {
            focus: () => {},
        } as TextBlockFeedbackEditorComponent;
        const focusSpy = vi.spyOn(component.feedbackEditor!, 'focus');
        component.select(true);
        vi.advanceTimersByTime(300);
        expect(focusSpy).toHaveBeenCalled();
    });

    it('should show text block', () => {
        const loremIpsum = 'Lorem Ipsum';
        component.textBlockRef.block!.text = loremIpsum;
        fixture.changeDetectorRef.detectChanges();

        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('span').textContent).toEqual(loremIpsum);
    });

    it('should only show Feedback Editor if Feedback is set', () => {
        let element = fixture.debugElement.query(By.directive(TextBlockFeedbackEditorComponent));
        expect(element).toBeFalsy();

        component.textBlockRef.initFeedback();
        component.textBlockRef.feedback!.gradingInstruction = new GradingInstruction();
        component.textBlockRef.feedback!.gradingInstruction.usageCount = 0;

        fixture.changeDetectorRef.detectChanges();
        element = fixture.debugElement.query(By.directive(TextBlockFeedbackEditorComponent));
        expect(element).toBeTruthy();
    });

    it('should delete feedback', () => {
        component.textBlockRef.initFeedback();
        fixture.changeDetectorRef.detectChanges();

        vi.spyOn(component.didDelete, 'emit');
        const feedbackEditor = fixture.debugElement.query(By.directive(TextBlockFeedbackEditorComponent));
        const feedbackEditorComponent = feedbackEditor.componentInstance as TextBlockFeedbackEditorComponent;
        feedbackEditorComponent.dismiss();

        expect(component.textBlockRef.feedback).toBeUndefined();
        expect(component.didDelete.emit).toHaveBeenCalledOnce();
        expect(component.didDelete.emit).toHaveBeenCalledWith(component.textBlockRef);
    });

    it('should delete feedback but not emit delete event when textblock is undeletable', () => {
        component.textBlockRef.initFeedback();
        component.textBlockRef.deletable = false;
        fixture.changeDetectorRef.detectChanges();

        vi.spyOn(component.didDelete, 'emit');
        const feedbackEditor = fixture.debugElement.query(By.directive(TextBlockFeedbackEditorComponent));
        const feedbackEditorComponent = feedbackEditor.componentInstance as TextBlockFeedbackEditorComponent;
        feedbackEditorComponent.dismiss();

        expect(component.textBlockRef.feedback).toBeUndefined();
        expect(component.didDelete.emit).not.toHaveBeenCalled();
    });

    it('should send assessment event when selecting automatic text block', () => {
        fixture.componentRef.setInput('selected', false);
        component.textBlockRef.feedback = {
            type: FeedbackType.MANUAL,
        };
        //@ts-ignore
        const sendAssessmentEvent = vi.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.select();
        fixture.changeDetectorRef.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC);
    });

    it('should not send assessment event when selecting text block that is unselectable', () => {
        fixture.componentRef.setInput('selected', false);
        component.textBlockRef.feedback = {
            type: FeedbackType.MANUAL,
        };
        component.textBlockRef.selectable = false;
        //@ts-ignore
        const sendAssessmentEvent = vi.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.select();
        fixture.changeDetectorRef.detectChanges();
        expect(sendAssessmentEvent).not.toHaveBeenCalled();
    });
});
