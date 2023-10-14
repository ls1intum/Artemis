import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess/textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text-block.model';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { TextAssessmentAnalytics } from 'app/exercises/text/assess/analytics/text-assesment-analytics.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgModel } from '@angular/forms';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { EventEmitter } from '@angular/core';

describe('TextblockAssessmentCardComponent', () => {
    let component: TextblockAssessmentCardComponent;
    let fixture: ComponentFixture<TextblockAssessmentCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockDirective(NgbTooltip)],
            declarations: [
                TextblockAssessmentCardComponent,
                TextblockFeedbackEditorComponent,
                TranslatePipeMock,
                MockDirective(TranslateDirective),
                MockDirective(NgModel),
                MockComponent(ConfirmIconComponent),
                MockComponent(GradingInstructionLinkIconComponent),
                MockComponent(AssessmentCorrectionRoundBadgeComponent),
                MockComponent(FaLayersComponent),
            ],
            providers: [MockProvider(StructuredGradingCriterionService), MockProvider(TextAssessmentAnalytics), MockProvider(TextAssessmentService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextblockAssessmentCardComponent);
                component = fixture.componentInstance;
                component.textBlockRef = TextBlockRef.new();
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('cannot be selected in readOnly mode', () => {
        component.readOnly = true;
        component.didSelect = new EventEmitter();
        const selectSpy = jest.spyOn(component.didSelect, 'emit');
        component.select();
        expect(selectSpy).not.toHaveBeenCalled();
    });

    it('should autofocus', () => {
        jest.useFakeTimers();
        component.readOnly = false;
        component.textBlockRef = TextBlockRef.new();
        component.textBlockRef.selectable = true;
        component.textBlockRef.feedback = {
            type: FeedbackType.MANUAL,
        };
        component.selected = false;
        component.feedbackEditor = {
            focus: () => {},
        } as TextblockFeedbackEditorComponent;
        const focusSpy = jest.spyOn(component.feedbackEditor!, 'focus');
        component.select(true);
        jest.advanceTimersByTime(300);
        expect(focusSpy).toHaveBeenCalled();
    });

    it('should show text block', () => {
        const loremIpsum = 'Lorem Ipsum';
        component.textBlockRef.block!.text = loremIpsum;
        fixture.detectChanges();

        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('span').textContent).toEqual(loremIpsum);
    });

    it('should only show Feedback Editor if Feedback is set', () => {
        let element = fixture.debugElement.query(By.directive(TextblockFeedbackEditorComponent));
        expect(element).toBeFalsy();

        component.textBlockRef.initFeedback();
        component.textBlockRef.feedback!.gradingInstruction = new GradingInstruction();
        component.textBlockRef.feedback!.gradingInstruction.usageCount = 0;

        fixture.detectChanges();
        element = fixture.debugElement.query(By.directive(TextblockFeedbackEditorComponent));
        expect(element).toBeTruthy();
    });

    it('should delete feedback', () => {
        component.textBlockRef.initFeedback();
        fixture.detectChanges();

        jest.spyOn(component.didDelete, 'emit');
        const feedbackEditor = fixture.debugElement.query(By.directive(TextblockFeedbackEditorComponent));
        const feedbackEditorComponent = feedbackEditor.componentInstance as TextblockFeedbackEditorComponent;
        feedbackEditorComponent.dismiss();

        expect(component.textBlockRef.feedback).toBeUndefined();
        expect(component.didDelete.emit).toHaveBeenCalledOnce();
        expect(component.didDelete.emit).toHaveBeenCalledWith(component.textBlockRef);
    });

    it('should delete feedback but not emit delete event when textblock is undeletable', () => {
        component.textBlockRef.initFeedback();
        component.textBlockRef.deletable = false;
        fixture.detectChanges();

        jest.spyOn(component.didDelete, 'emit');
        const feedbackEditor = fixture.debugElement.query(By.directive(TextblockFeedbackEditorComponent));
        const feedbackEditorComponent = feedbackEditor.componentInstance as TextblockFeedbackEditorComponent;
        feedbackEditorComponent.dismiss();

        expect(component.textBlockRef.feedback).toBeUndefined();
        expect(component.didDelete.emit).not.toHaveBeenCalled();
    });

    it('should send assessment event when selecting automatic text block', () => {
        component.selected = false;
        component.textBlockRef.feedback = {
            type: FeedbackType.MANUAL,
        };
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.select();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC);
    });

    it('should not send assessment event when selecting text block that is unselectable', () => {
        component.selected = false;
        component.textBlockRef.feedback = {
            type: FeedbackType.MANUAL,
        };
        component.textBlockRef.selectable = false;
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.select();
        fixture.detectChanges();
        expect(sendAssessmentEvent).not.toHaveBeenCalled();
    });
});
