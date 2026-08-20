import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockModule } from 'ng-mocks';
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
        TestBed.configureTestingModule({
            imports: [CodeEditorTutorAssessmentInlineFeedbackComponent, MockModule(NgbTooltipModule)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, StructuredGradingCriterionService],
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

    it('should delete feedback after confirming on the trash icon while editing', () => {
        fixture.componentRef.setInput('feedback', { type: FeedbackType.MANUAL, credits: 1 } as Feedback);
        comp.editFeedback(codeLine);
        fixture.detectChanges();

        const onDeleteFeedbackSpy = vi.fn();
        comp.onDeleteFeedback.subscribe(onDeleteFeedbackSpy);

        const confirmIcon = fixture.debugElement.query(By.css('jhi-confirm-icon'));
        expect(confirmIcon).not.toBeNull();

        confirmIcon.triggerEventHandler('confirmEvent', true);

        expect(onDeleteFeedbackSpy).toHaveBeenCalledOnce();
        expect(onDeleteFeedbackSpy).toHaveBeenCalledWith(comp.currentFeedback());
    });

    it('should show delete control in view mode without opening the editor', () => {
        fixture.componentRef.setInput('feedback', { type: FeedbackType.MANUAL, credits: 1, detailText: 'comment' } as Feedback);
        fixture.detectChanges();

        expect(comp.viewOnly()).toBe(true);
        expect(fixture.debugElement.query(By.css('jhi-confirm-icon'))).not.toBeNull();
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

        const pointsElement = fixture.debugElement.query(By.css('.inline-feedback__points-pill'));
        expect(pointsElement).toBeNull();
    });

    it('should display credits and icons for graded feedback', () => {
        fixture.componentRef.setInput('feedback', {
            credits: 1,
            type: FeedbackType.AUTOMATIC,
            text: 'feedback',
        } as Feedback);
        fixture.detectChanges();

        const pointsElement = fixture.debugElement.query(By.css('.inline-feedback__points-pill'));
        expect(pointsElement).not.toBeNull();
        expect(pointsElement.nativeElement.textContent).toContain('1P');
    });

    it('should use the correct translation key for non-graded feedback', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.AUTOMATIC,
            text: NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER + 'feedback',
        } as Feedback);
        fixture.detectChanges();

        const labelElement = fixture.debugElement.query(By.css('.inline-feedback__label')).nativeElement;
        expect(labelElement.attributes['jhiTranslate'].value).toBe('artemisApp.assessment.detail.feedback');
        const paragraphElement = fixture.debugElement.query(By.css('.inline-feedback__text')).nativeElement;
        expect(paragraphElement.innerHTML).toContain(comp.buildFeedbackTextForCodeEditor(comp.currentFeedback()));
    });

    it('should use the feedback label for graded feedback', () => {
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            text: 'feedback',
        } as Feedback);
        fixture.detectChanges();

        const labelElement = fixture.debugElement.query(By.css('.inline-feedback__label')).nativeElement;
        expect(labelElement.attributes['jhiTranslate'].value).toBe('artemisApp.assessment.detail.feedback');
        const paragraphElement = fixture.debugElement.query(By.css('.inline-feedback__text')).nativeElement;
        expect(paragraphElement.innerHTML).toContain(comp.buildFeedbackTextForCodeEditor(comp.currentFeedback()));
    });

    it('should show the criterion title for instruction-linked feedback', () => {
        const instruction = { id: 7, credits: 1, gradingScale: 'good', instructionDescription: 'desc', feedback: 'inst', usageCount: 0 };
        fixture.componentRef.setInput('gradingCriteria', [{ id: 1, title: 'Player', structuredGradingInstructions: [instruction] }]);
        fixture.componentRef.setInput('feedback', {
            type: FeedbackType.MANUAL,
            detailText: 'Ok',
            gradingInstruction: instruction,
        } as Feedback);
        fixture.detectChanges();

        expect(comp.displayTitle()).toBe('Player');
        expect(fixture.debugElement.query(By.css('.inline-feedback__title'))?.nativeElement.textContent).toBe('Player');
    });

    it('should step the points in half-point increments while editing', () => {
        const feedback = new Feedback();
        feedback.credits = 1;
        fixture.componentRef.setInput('feedback', feedback);
        comp.editFeedback(codeLine);

        comp['stepCredits'](0.5);
        expect(comp.currentFeedback().credits).toBe(1.5);

        comp['stepCredits'](-0.5);
        expect(comp.currentFeedback().credits).toBe(1);
    });

    it('should keep the points input, tone, and save button in sync with currentFeedback', () => {
        const feedback = new Feedback();
        feedback.credits = 0;
        fixture.componentRef.setInput('feedback', feedback);
        comp.editFeedback(codeLine);
        fixture.detectChanges();

        const card = fixture.nativeElement.querySelector('.inline-feedback') as HTMLElement;
        const steps = fixture.nativeElement.querySelectorAll('.inline-feedback__step') as NodeListOf<HTMLButtonElement>;

        expect(Number((fixture.nativeElement.querySelector('#feedback-points') as HTMLInputElement).value)).toBe(0);
        expect(card.getAttribute('data-tone')).toBe('neutral');

        steps[1].click();
        fixture.detectChanges();

        const input = fixture.nativeElement.querySelector('#feedback-points') as HTMLInputElement;
        expect(comp.currentFeedback().credits).toBe(0.5);
        expect(card.getAttribute('data-tone')).toBe('positive');
        expect(input.value).toBe('0.5');
        expect((fixture.nativeElement.querySelector('#feedback-save') as HTMLButtonElement).disabled).toBe(false);
    });

    it('should normalize typed points before saving', () => {
        comp['updateCredits'](0.3);
        expect(comp.currentFeedback().credits).toBe(0.5);

        const onUpdateFeedbackSpy = vi.fn();
        comp.onUpdateFeedback.subscribe(onUpdateFeedbackSpy);
        comp.updateFeedback();

        expect(onUpdateFeedbackSpy).toHaveBeenCalledWith(expect.objectContaining({ credits: 0.5 }));
    });

    it('should not step the points of a feedback linked to a grading instruction', () => {
        const feedback = new Feedback();
        feedback.credits = 2;
        feedback.gradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description', usageCount: 0 };
        fixture.componentRef.setInput('feedback', feedback);

        comp['stepCredits'](0.5);

        expect(comp.currentFeedback().credits).toBe(2);
    });
});
