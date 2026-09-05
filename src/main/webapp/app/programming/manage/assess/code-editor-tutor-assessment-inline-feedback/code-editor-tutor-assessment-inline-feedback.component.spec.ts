import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockModule, MockProvider } from 'ng-mocks';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/programming/manage/assess/code-editor-tutor-assessment-inline-feedback/code-editor-tutor-assessment-inline-feedback.component';
import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    Feedback,
    FeedbackType,
    NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER,
} from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';

describe('CodeEditorTutorAssessmentInlineFeedbackComponent', () => {
    let comp: CodeEditorTutorAssessmentInlineFeedbackComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    let sgiService: StructuredGradingCriterionService;
    const fileName = 'testFile';
    const codeLine = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CodeEditorTutorAssessmentInlineFeedbackComponent, MockModule(NgbTooltipModule)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(StructuredGradingCriterionService),
                // The edit-mode delete button (rendered for MANUAL feedback) pulls in the delete dialog service.
                MockProvider(DeleteDialogService),
            ],
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
        expect(comp.currentFeedback().text).toBe(`File ${fileName} at line ${codeLine + 1}`);
    });

    it('should keep the suggestion prefix in the title when an SGI is dropped on a feedback suggestion', () => {
        const suggestionText = `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check`;
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            text: suggestionText,
        } as Feedback);
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        // Fake call as a DragEvent cannot be created programmatically
        vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation((feedback: Feedback) => {
            feedback.gradingInstruction = instruction;
            feedback.credits = instruction.credits;
        });

        comp.updateFeedbackOnDrop(new Event(''));

        expect(comp.currentFeedback().gradingInstruction).toEqual(instruction);
        expect(comp.currentFeedback().reference).toBe(`file:${fileName}_line:${codeLine}`);
        // The auto-generated title must not overwrite the suggestion identity.
        expect(comp.currentFeedback().text).toBe(suggestionText);
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

    it('should not display a points pill for non-graded feedback suggestions', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.AUTOMATIC,
            text: NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER + 'feedback',
        } as Feedback);
        fixture.detectChanges();

        const pointsElement = fixture.debugElement.query(By.css('.unified-feedback-points'));
        expect(pointsElement).toBeNull();
    });

    it('should display a points pill for graded feedback', () => {
        fixture.componentRef.setInput('feedback', {
            credits: 1,
            type: FeedbackType.AUTOMATIC,
            text: 'feedback',
        } as Feedback);
        fixture.detectChanges();

        const pointsElement = fixture.debugElement.query(By.css('.unified-feedback-points'));
        expect(pointsElement).not.toBeNull();
        expect(pointsElement.nativeElement.textContent).toContain('+1');
    });

    it('should render the feedback content for non-graded feedback suggestions', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.AUTOMATIC,
            text: NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER + 'feedback',
            detailText: 'Consider extracting this into a helper method.',
        } as Feedback);
        fixture.detectChanges();

        const contentElement = fixture.debugElement.query(By.css('.unified-feedback-text')).nativeElement;
        expect(contentElement.innerHTML).toContain(comp.buildFeedbackTextForCodeEditor(comp.currentFeedback()));
    });

    it('should render the feedback content for graded feedback', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            text: 'feedback',
            detailText: 'Off-by-one error on this line.',
        } as Feedback);
        fixture.detectChanges();

        const contentElement = fixture.debugElement.query(By.css('.unified-feedback-text')).nativeElement;
        expect(contentElement.innerHTML).toContain(comp.buildFeedbackTextForCodeEditor(comp.currentFeedback()));
    });

    it('should render an editable title field for a feedback suggestion while editing', async () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            text: `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check`,
            detailText: 'Add a null check.',
            credits: 1,
        } as Feedback);
        comp.editFeedback(codeLine);
        fixture.detectChanges();
        // The app is zoneless: the ngModel-bound input value is only written to the DOM after an extra stabilization pass.
        await fixture.whenStable();
        fixture.detectChanges();

        const titleInput = fixture.debugElement.query(By.css('.unified-feedback-title-input'));
        expect(titleInput).toBeTruthy();
        expect(titleInput.nativeElement.value).toBe('Missing null check');
    });

    it('should not render an editable title field for a non-suggestion feedback while editing', async () => {
        // The title of a non-suggestion feedback is auto-generated on save, so it must not be offered as an input.
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            text: 'File testFile at line 2',
            detailText: 'Add a null check.',
            credits: 1,
        } as Feedback);
        comp.editFeedback(codeLine);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.unified-feedback-title-input'))).toBeNull();
    });

    it('should show the suggestion badge only while editing, not in the collapsed view', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            text: `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check`,
            detailText: 'Add a null check.',
            credits: 1,
        } as Feedback);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('jhi-feedback-suggestion-badge'))).toBeNull();

        comp.editFeedback(codeLine);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('jhi-feedback-suggestion-badge'))).toBeTruthy();
    });

    it('should mark an accepted suggestion as adapted when its detail text is edited while editing', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            text: `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check`,
            detailText: 'Add a null check.',
            credits: 1,
        } as Feedback);
        comp.editFeedback(codeLine);
        fixture.detectChanges();

        const detailTextarea = fixture.debugElement.query(By.css('.unified-feedback-detail-input')).nativeElement as HTMLTextAreaElement;
        detailTextarea.value = 'Add a null check before dereferencing the pointer.';
        detailTextarea.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(comp.currentFeedback().text).toBe(`${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Missing null check`);
    });

    it('should render the collapsed view through the unified feedback card in read-only mode', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            text: 'File testFile at line 2',
            detailText: 'Add a null check.',
            credits: 1,
        } as Feedback);
        fixture.detectChanges();

        const unifiedFeedback = fixture.debugElement.query(By.directive(UnifiedFeedbackComponent));
        expect(unifiedFeedback).toBeTruthy();
        expect(unifiedFeedback.componentInstance.editable()).toBe(false);
    });

    it('should cancel the open edit when the built-in dismiss button is clicked', () => {
        const onCancelFeedbackSpy = vi.fn();
        comp.onCancelFeedback.subscribe(onCancelFeedbackSpy);
        // A fresh, empty feedback is dismissible without confirmation, so the plain dismiss button renders.
        fixture.detectChanges();

        const dismissButton = fixture.debugElement.query(By.css('#dismiss-icon'));
        expect(dismissButton).toBeTruthy();
        dismissButton.nativeElement.click();
        fixture.detectChanges();

        expect(onCancelFeedbackSpy).toHaveBeenCalledOnce();
        expect(onCancelFeedbackSpy).toHaveBeenCalledWith(codeLine);
    });
});
