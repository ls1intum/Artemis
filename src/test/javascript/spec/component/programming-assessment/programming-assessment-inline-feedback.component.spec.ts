import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockProvider, MockPipe, MockModule } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/assessment-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

describe('CodeEditorTutorAssessmentInlineFeedbackComponent', () => {
    let comp: CodeEditorTutorAssessmentInlineFeedbackComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    let sgiService: StructuredGradingCriterionService;
    const fileName = 'testFile';
    const codeLine = 1;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [
                CodeEditorTutorAssessmentInlineFeedbackComponent,
                MockComponent(GradingInstructionLinkIconComponent),
                MockComponent(AssessmentCorrectionRoundBadgeComponent),
                MockComponent(FaIconComponent),
                MockDirective(NgModel),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(StructuredGradingCriterionService)],
        })
            .compileComponents()
            .then(() => {
                // Ignore console errors
                console.error = () => {
                    return false;
                };
                fixture = TestBed.createComponent(CodeEditorTutorAssessmentInlineFeedbackComponent);
                comp = fixture.componentInstance;
                // @ts-ignore
                comp.feedback = undefined;
                comp.readOnly = false;
                comp.selectedFile = fileName;
                comp.codeLine = codeLine;
                sgiService = fixture.debugElement.injector.get(StructuredGradingCriterionService);
            });
    });

    it('should update feedback and emit to parent', () => {
        const onUpdateFeedbackSpy = jest.spyOn(comp.onUpdateFeedback, 'emit');
        comp.updateFeedback();

        expect(comp.feedback.reference).toBe(`file:${fileName}_line:${codeLine}`);
        expect(comp.feedback.type).toBe(FeedbackType.MANUAL);

        expect(onUpdateFeedbackSpy).toHaveBeenCalledOnce();
        expect(onUpdateFeedbackSpy).toHaveBeenCalledWith(comp.feedback);
    });

    it('should enable edit feedback and emit to parent', () => {
        const onEditFeedbackSpy = jest.spyOn(comp.onEditFeedback, 'emit');
        comp.editFeedback(codeLine);

        expect(onEditFeedbackSpy).toHaveBeenCalledOnce();
        expect(onEditFeedbackSpy).toHaveBeenCalledWith(codeLine);
    });

    it('should cancel feedback and emit to parent', () => {
        const onCancelFeedbackSpy = jest.spyOn(comp.onCancelFeedback, 'emit');
        comp.cancelFeedback();

        expect(onCancelFeedbackSpy).toHaveBeenCalledOnce();
        expect(onCancelFeedbackSpy).toHaveBeenCalledWith(codeLine);
    });

    it('should delete feedback and emit to parent', () => {
        const onDeleteFeedbackSpy = jest.spyOn(comp.onDeleteFeedback, 'emit');
        global.confirm = () => true;
        const confirmSpy = jest.spyOn(window, 'confirm');
        comp.deleteFeedback();

        expect(confirmSpy).toHaveBeenCalledOnce();
        expect(confirmSpy).toHaveBeenCalledWith('artemisApp.feedback.delete.question');

        expect(onDeleteFeedbackSpy).toHaveBeenCalledOnce();
        expect(onDeleteFeedbackSpy).toHaveBeenCalledWith(comp.feedback);
    });

    it('should update feedback with SGI and emit to parent', () => {
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        // Fake call as a DragEvent cannot be created programmatically
        jest.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation(() => {
            comp.feedback.gradingInstruction = instruction;
            comp.feedback.credits = instruction.credits;
        });
        // Call spy function with empty event
        comp.updateFeedbackOnDrop(new Event(''));

        expect(comp.feedback.gradingInstruction).toEqual(instruction);
        expect(comp.feedback.credits).toEqual(instruction.credits);
        expect(comp.feedback.reference).toBe(`file:${fileName}_line:${codeLine}`);
    });

    it('should count feedback with one credit as positive', () => {
        comp.feedback = new Feedback();
        comp.feedback.credits = 1;

        comp.updateFeedback();

        expect(comp.feedback.positive).toBeTrue();
    });

    it('should display the feedback text properly', () => {
        const gradingInstruction = {
            id: 1,
            credits: 1,
            gradingScale: 'scale',
            instructionDescription: 'description',
            feedback: 'instruction feedback',
            usageCount: 0,
        } as GradingInstruction;
        const feedback = {
            id: 1,
            detailText: 'feedback1',
            text: 'File src/sorting/BubbleSort.java at line 4',
            credits: 1.5,
        } as Feedback;

        let textToBeDisplayed = comp.buildFeedbackTextForCodeEditor(feedback);
        expect(textToBeDisplayed).toBe(feedback.detailText);

        feedback.gradingInstruction = gradingInstruction;
        textToBeDisplayed = comp.buildFeedbackTextForCodeEditor(feedback);
        expect(textToBeDisplayed).toEqual(gradingInstruction.feedback + '<br>' + feedback.detailText);
    });
});
