import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
    setupTestBed({ zoneless: true });

    let comp: CodeEditorTutorAssessmentInlineFeedbackComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    let sgiService: StructuredGradingCriterionService;
    const fileName = 'testFile';
    const codeLine = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CodeEditorTutorAssessmentInlineFeedbackComponent, MockModule(NgbTooltipModule)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(StructuredGradingCriterionService)],
        });
        fixture = TestBed.createComponent(CodeEditorTutorAssessmentInlineFeedbackComponent);
        comp = fixture.componentInstance;
        // No feedback bound -> working copy defaults to a fresh Feedback (viewOnly = false), mirroring the original setter.
        fixture.componentRef.setInput('feedback', undefined);
        fixture.componentRef.setInput('readOnly', false);
        fixture.componentRef.setInput('selectedFile', fileName);
        fixture.componentRef.setInput('codeLine', codeLine);
        sgiService = TestBed.inject(StructuredGradingCriterionService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should update feedback and emit to parent', () => {
        const onUpdateFeedbackSpy = vi.fn();
        comp.onUpdateFeedback.subscribe(onUpdateFeedbackSpy);
        comp.updateFeedback();

        expect(comp.currentFeedback().reference).toBe(`file:${fileName}_line:${codeLine}`);
        expect(comp.currentFeedback().type).toBe(FeedbackType.MANUAL);

        expect(onUpdateFeedbackSpy).toHaveBeenCalledOnce();
        expect(onUpdateFeedbackSpy).toHaveBeenCalledWith(comp.currentFeedback());
    });

    it('should enable edit feedback and emit to parent', () => {
        const onEditFeedbackSpy = vi.fn();
        comp.onEditFeedback.subscribe(onEditFeedbackSpy);
        comp.editFeedback(codeLine);

        expect(onEditFeedbackSpy).toHaveBeenCalledOnce();
        expect(onEditFeedbackSpy).toHaveBeenCalledWith(codeLine);
    });

    it('should cancel feedback and emit to parent', () => {
        const onCancelFeedbackSpy = vi.fn();
        comp.onCancelFeedback.subscribe(onCancelFeedbackSpy);
        comp.cancelFeedback();

        expect(onCancelFeedbackSpy).toHaveBeenCalledOnce();
        expect(onCancelFeedbackSpy).toHaveBeenCalledWith(codeLine);
    });

    it('should delete feedback and emit to parent', () => {
        const onDeleteFeedbackSpy = vi.fn();
        comp.onDeleteFeedback.subscribe(onDeleteFeedbackSpy);
        comp.deleteFeedback();

        expect(onDeleteFeedbackSpy).toHaveBeenCalledOnce();
        expect(onDeleteFeedbackSpy).toHaveBeenCalledWith(comp.currentFeedback());
    });

    it('should update feedback with SGI and emit to parent', () => {
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        // Fake call as a DragEvent cannot be created programmatically
        vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation((feedback: Feedback) => {
            feedback.gradingInstruction = instruction;
            feedback.credits = instruction.credits;
        });
        // Call spy function with empty event
        comp.updateFeedbackOnDrop(new Event(''));

        expect(comp.currentFeedback().gradingInstruction).toEqual(instruction);
        expect(comp.currentFeedback().credits).toEqual(instruction.credits);
        expect(comp.currentFeedback().reference).toBe(`file:${fileName}_line:${codeLine}`);
    });

    it('should count feedback with one credit as positive', () => {
        const feedbackWithCredit = new Feedback();
        feedbackWithCredit.credits = 1;
        fixture.componentRef.setInput('feedback', feedbackWithCredit);

        comp.updateFeedback();

        expect(comp.currentFeedback().positive).toBe(true);
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
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.AUTOMATIC,
            text: NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER + 'feedback',
        } as Feedback);
        fixture.detectChanges();

        const badgeElement = fixture.debugElement.query(By.css('.badge'));
        expect(badgeElement).toBeNull();
    });

    it('should display credits and icons for graded feedback', () => {
        fixture.componentRef.setInput('feedback', {
            credits: 1,
            type: FeedbackType.AUTOMATIC,
            text: 'feedback',
        } as Feedback);
        fixture.detectChanges();

        const badgeElement = fixture.debugElement.query(By.css('.badge'));
        expect(badgeElement).not.toBeNull();
        expect(badgeElement.nativeElement.textContent).toContain('1P');
    });

    it('should use the correct translation key for non-graded feedback', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.AUTOMATIC,
            text: NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER + 'feedback',
        } as Feedback);
        fixture.detectChanges();

        const headerElement = fixture.debugElement.query(By.css('.col-10 h6')).nativeElement;
        expect(headerElement.attributes['jhiTranslate'].value).toBe('artemisApp.assessment.detail.feedback');
        const paragraphElement = fixture.debugElement.query(By.css('.col-10 p')).nativeElement;
        expect(paragraphElement.innerHTML).toContain(comp.buildFeedbackTextForCodeEditor(comp.currentFeedback()));
    });

    it('should use the correct translation key for graded feedback', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            text: 'feedback',
        } as Feedback);
        fixture.detectChanges();

        const headerElement = fixture.debugElement.query(By.css('.col-10 h6')).nativeElement;
        expect(headerElement.attributes['jhiTranslate'].value).toBe('artemisApp.assessment.detail.tutorComment');
        const paragraphElement = fixture.debugElement.query(By.css('.col-10 p')).nativeElement;
        expect(paragraphElement.innerHTML).toContain(comp.buildFeedbackTextForCodeEditor(comp.currentFeedback()));
    });
});
