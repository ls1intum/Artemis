import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockModule, MockProvider } from 'ng-mocks';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/programming/manage/assess/code-editor-tutor-assessment-inline-feedback/code-editor-tutor-assessment-inline-feedback.component';
import { Feedback, FeedbackType, NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';

describe('CodeEditorTutorAssessmentInlineFeedbackComponent', () => {
    let comp: CodeEditorTutorAssessmentInlineFeedbackComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    let sgiService: StructuredGradingCriterionService;
    const fileName = 'testFile';
    const codeLine = 1;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
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
                sgiService = TestBed.inject(StructuredGradingCriterionService);
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
        comp.deleteFeedback();

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

    it('should escape special characters', () => {
        const feedbackWithSpecialCharacters = {
            detailText: 'feedback <with> special characters & "',
        } as Feedback;
        const expectedTextToBeDisplayed = 'feedback &lt;with&gt; special characters &amp; &quot;';

        const textToBeDisplayed = comp.buildFeedbackTextForCodeEditor(feedbackWithSpecialCharacters);
        expect(textToBeDisplayed).toEqual(expectedTextToBeDisplayed);
    });

    it('should not display credits and icons for non-graded feedback suggestions', () => {
        comp.feedback = {
            type: FeedbackType.AUTOMATIC,
            text: NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER + 'feedback',
        } as Feedback;
        fixture.detectChanges();

        const badgeElement = fixture.debugElement.query(By.css('.badge'));
        expect(badgeElement).toBeNull();
    });

    it('should display credits and icons for graded feedback', () => {
        comp.feedback = {
            credits: 1,
            type: FeedbackType.AUTOMATIC,
            text: 'feedback',
        } as Feedback;
        fixture.detectChanges();

        const badgeElement = fixture.debugElement.query(By.css('.badge'));
        expect(badgeElement).not.toBeNull();
        expect(badgeElement.nativeElement.textContent).toContain('1P');
    });

    it('should use the correct translation key for non-graded feedback', () => {
        comp.feedback = {
            type: FeedbackType.AUTOMATIC,
            text: NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER + 'feedback',
        } as Feedback;
        fixture.detectChanges();

        const headerElement = fixture.debugElement.query(By.css('.col-10 h6')).nativeElement;
        expect(headerElement.attributes['jhiTranslate'].value).toBe('artemisApp.assessment.detail.feedback');
        const paragraphElement = fixture.debugElement.query(By.css('.col-10 p')).nativeElement;
        expect(paragraphElement.innerHTML).toContain(comp.buildFeedbackTextForCodeEditor(comp.feedback));
    });

    it('should use the correct translation key for graded feedback', () => {
        comp.feedback = {
            type: FeedbackType.MANUAL,
            text: 'feedback',
        } as Feedback;
        fixture.detectChanges();

        const headerElement = fixture.debugElement.query(By.css('.col-10 h6')).nativeElement;
        expect(headerElement.attributes['jhiTranslate'].value).toBe('artemisApp.assessment.detail.tutorComment');
        const paragraphElement = fixture.debugElement.query(By.css('.col-10 p')).nativeElement;
        expect(paragraphElement.innerHTML).toContain(comp.buildFeedbackTextForCodeEditor(comp.feedback));
    });

    it('should cancel feedback on Escape', () => {
        const cancelFeedbackSpy = jest.spyOn(comp, 'cancelFeedback');
        (comp as any).handleKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));
        expect(cancelFeedbackSpy).toHaveBeenCalledOnce();
    });

    it('should save feedback on Shift+Enter only when credits are defined', () => {
        const updateFeedbackSpy = jest.spyOn(comp, 'updateFeedback');
        const shiftEnter = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true });

        comp.feedback.credits = undefined;
        (comp as any).handleKeydown(shiftEnter);
        expect(updateFeedbackSpy).not.toHaveBeenCalled();

        comp.feedback.credits = 1;
        (comp as any).handleKeydown(shiftEnter);
        expect(updateFeedbackSpy).toHaveBeenCalledOnce();
    });
});
