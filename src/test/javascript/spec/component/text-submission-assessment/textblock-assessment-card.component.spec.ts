import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess/textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { MockComponent, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/assessment-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisGradingInstructionLinkIconModule } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.module';
import { ChangeDetectorRef } from '@angular/core';

describe('TextblockAssessmentCardComponent', () => {
    let component: TextblockAssessmentCardComponent;
    let fixture: ComponentFixture<TextblockAssessmentCardComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, TranslateModule.forRoot(), ArtemisConfirmIconModule, ArtemisGradingInstructionLinkIconModule],
            declarations: [TextblockAssessmentCardComponent, TextblockFeedbackEditorComponent, AssessmentCorrectionRoundBadgeComponent],
            providers: [MockProvider(ChangeDetectorRef)],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextblockAssessmentCardComponent);
        component = fixture.componentInstance;
        component.textBlockRef = TextBlockRef.new();
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show text block', () => {
        const loremIpsum = 'Lorem Ipsum';
        component.textBlockRef.block!.text = loremIpsum;
        fixture.detectChanges();

        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('span').innerText).toEqual(loremIpsum);
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

        spyOn(component.didDelete, 'emit');
        const feedbackEditor = fixture.debugElement.query(By.directive(TextblockFeedbackEditorComponent));
        const feedbackEditorComponent = feedbackEditor.componentInstance as TextblockFeedbackEditorComponent;
        feedbackEditorComponent.dismiss();

        expect(component.textBlockRef.feedback).toBe(undefined);
        expect(component.didDelete.emit).toHaveBeenCalledTimes(1);
        expect(component.didDelete.emit).toHaveBeenCalledWith(component.textBlockRef);
    });
});
