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
import { deepClone } from 'app/foundation/util/deep-clone.util';

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
        comp.currentFeedback().detailText = 'note';
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
        const onPendingSpy = vi.fn();
        comp.onCancelFeedback.subscribe(onCancelFeedbackSpy);
        comp.onPendingFeedbackChange.subscribe(onPendingSpy);
        comp.cancelFeedback();

        expect(onCancelFeedbackSpy).toHaveBeenCalledOnce();
        expect(onCancelFeedbackSpy).toHaveBeenCalledWith(codeLine);
        expect(onPendingSpy).toHaveBeenCalledExactlyOnceWith(undefined);
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
        expect(onDeleteFeedbackSpy).toHaveBeenCalledWith(comp.feedback());
    });

    it('should delete the original feedback after editing points and comment', () => {
        const original = {
            id: 42,
            type: FeedbackType.MANUAL,
            credits: 1,
            text: 'File testFile at line 2',
            detailText: 'old comment',
            reference: `file:${fileName}_line:${codeLine}`,
        } as Feedback;
        fixture.componentRef.setInput('feedback', original);
        fixture.detectChanges();
        comp.editFeedback(codeLine);

        // Textarea mutates the list item in place before a point edit detaches currentFeedback.
        comp.currentFeedback().detailText = 'edited before points';
        comp['stepCredits'](0.5);
        comp.currentFeedback().detailText = 'new comment';

        const onDeleteFeedbackSpy = vi.fn();
        comp.onDeleteFeedback.subscribe(onDeleteFeedbackSpy);
        comp.deleteFeedback();

        expect(onDeleteFeedbackSpy).toHaveBeenCalledOnce();
        const emitted = onDeleteFeedbackSpy.mock.calls[0][0] as Feedback;
        expect(emitted).toBe(original);
        expect(Feedback.areIdentical(emitted, original)).toBe(true);
        expect(Feedback.areIdentical(comp.currentFeedback(), original)).toBe(false);
        expect(Feedback.areIdentical(comp.oldFeedback(), original)).toBe(false);
    });

    it('should show delete control in view mode without opening the editor', () => {
        fixture.componentRef.setInput('feedback', { type: FeedbackType.MANUAL, credits: 1, detailText: 'comment' } as Feedback);
        fixture.detectChanges();

        expect(comp.viewOnly()).toBe(true);
        expect(fixture.debugElement.query(By.css('jhi-confirm-icon'))).not.toBeNull();
    });

    it('should update feedback with SGI and emit pending change for unsaved cards', () => {
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        // Fake call as a DragEvent cannot be created programmatically
        vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation((feedback: Feedback) => {
            feedback.gradingInstruction = instruction;
            feedback.credits = instruction.credits;
        });
        const onPendingSpy = vi.fn();
        comp.onPendingFeedbackChange.subscribe(onPendingSpy);

        comp.updateFeedbackOnDrop(new Event(''));

        expect(comp.currentFeedback().gradingInstruction).toEqual(instruction);
        expect(comp.currentFeedback().credits).toEqual(instruction.credits);
        expect(comp.currentFeedback().reference).toBe(`file:${fileName}_line:${codeLine}`);
        expect(onPendingSpy).toHaveBeenCalledExactlyOnceWith(comp.currentFeedback());
    });

    it('should emit onUpdateFeedback for SGI drop on an existing card', () => {
        const existing = { type: FeedbackType.MANUAL, credits: 1, detailText: 'note', reference: `file:${fileName}_line:${codeLine}` } as Feedback;
        fixture.componentRef.setInput('feedback', existing);
        fixture.detectChanges();
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description', usageCount: 0 };
        vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation((feedback: Feedback) => {
            feedback.gradingInstruction = instruction;
            feedback.credits = instruction.credits;
        });
        const onUpdateSpy = vi.fn();
        const onPendingSpy = vi.fn();
        comp.onUpdateFeedback.subscribe(onUpdateSpy);
        comp.onPendingFeedbackChange.subscribe(onPendingSpy);
        comp.editFeedback(codeLine);

        comp.updateFeedbackOnDrop(new Event(''));

        expect(onUpdateSpy).toHaveBeenCalledExactlyOnceWith(comp.currentFeedback());
        expect(onPendingSpy).not.toHaveBeenCalled();
    });

    it('should restore and emit onUpdateFeedback when canceling an existing card edit', () => {
        const existing = {
            type: FeedbackType.MANUAL,
            credits: 1,
            detailText: 'note',
            reference: `file:${fileName}_line:${codeLine}`,
        } as Feedback;
        fixture.componentRef.setInput('feedback', existing);
        fixture.detectChanges();
        comp.editFeedback(codeLine);
        comp.currentFeedback().gradingInstruction = {
            id: 1,
            credits: 2,
            feedback: 'test',
            gradingScale: 'good',
            instructionDescription: 'description',
            usageCount: 0,
        };
        const onUpdateSpy = vi.fn();
        const onCancelSpy = vi.fn();
        comp.onUpdateFeedback.subscribe(onUpdateSpy);
        comp.onCancelFeedback.subscribe(onCancelSpy);

        comp.cancelFeedback();

        expect(onUpdateSpy).toHaveBeenCalledOnce();
        expect(onUpdateSpy.mock.calls[0][0].gradingInstruction).toBeUndefined();
        expect(onCancelSpy).not.toHaveBeenCalled();
        expect(comp.viewOnly()).toBe(true);
    });

    it('should restore the edit-start snapshot after point edit, instruction drop, and cancel', () => {
        const existing = {
            type: FeedbackType.MANUAL,
            credits: 1,
            detailText: 'note',
            reference: `file:${fileName}_line:${codeLine}`,
        } as Feedback;
        fixture.componentRef.setInput('feedback', existing);
        fixture.detectChanges();
        comp.editFeedback(codeLine);
        comp['stepCredits'](0.5);

        const instruction: GradingInstruction = {
            id: 1,
            credits: 2,
            feedback: 'test',
            gradingScale: 'good',
            instructionDescription: 'description',
            usageCount: 0,
        };
        vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation((feedback: Feedback) => {
            feedback.gradingInstruction = instruction;
            feedback.credits = instruction.credits;
        });

        // Parent (Monaco) writes the emitted draft back into the feedback input — must not overwrite the cancel snapshot.
        comp.onUpdateFeedback.subscribe((draft) => {
            fixture.componentRef.setInput('feedback', draft);
            fixture.detectChanges();
        });

        comp.updateFeedbackOnDrop(new Event(''));
        expect(comp.currentFeedback().gradingInstruction).toEqual(instruction);
        expect(comp.currentFeedback().credits).toBe(2);

        const restoredEmits: Feedback[] = [];
        comp.onUpdateFeedback.subscribe((feedback) => restoredEmits.push(feedback));
        comp.cancelFeedback();

        expect(restoredEmits).toHaveLength(1);
        expect(restoredEmits[0].credits).toBe(1);
        expect(restoredEmits[0].gradingInstruction).toBeUndefined();
        expect(restoredEmits[0].detailText).toBe('note');
        expect(comp.currentFeedback().credits).toBe(1);
        expect(comp.currentFeedback().gradingInstruction).toBeUndefined();
        expect(comp.viewOnly()).toBe(true);
    });

    it('should keep the editor open when Monaco rebinds a cloned instruction update and restore on cancel', () => {
        const existing = {
            type: FeedbackType.MANUAL,
            credits: 1,
            detailText: 'note',
            reference: `file:${fileName}_line:${codeLine}`,
        } as Feedback;
        fixture.componentRef.setInput('feedback', existing);
        fixture.detectChanges();
        comp.editFeedback(codeLine);
        fixture.detectChanges();

        // Detaches the working copy from the bound list item, so the emit below rebinds the input to a new reference.
        fixture.debugElement.queryAll(By.css('.inline-feedback__step'))[1].nativeElement.click();
        fixture.detectChanges();

        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description', usageCount: 0 };
        vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation((feedback: Feedback) => {
            feedback.gradingInstruction = instruction;
            feedback.credits = instruction.credits;
        });
        comp.onUpdateFeedback.subscribe((draft) => {
            fixture.componentRef.setInput('feedback', deepClone(draft));
            fixture.detectChanges();
        });

        comp.updateFeedbackOnDrop(new Event(''));
        fixture.detectChanges();

        expect(comp.viewOnly()).toBe(false);
        expect(fixture.debugElement.query(By.css('#feedback-textarea'))).not.toBeNull();

        fixture.debugElement.queryAll(By.css('.inline-feedback__footer button'))[0].nativeElement.click();
        fixture.detectChanges();

        expect(comp.viewOnly()).toBe(true);
        expect(comp.currentFeedback().credits).toBe(1);
        expect(comp.currentFeedback().detailText).toBe('note');
        expect(comp.currentFeedback().gradingInstruction).toBeUndefined();
    });

    it('should count feedback with one credit as positive', () => {
        const feedbackWithCredit = new Feedback();
        feedbackWithCredit.credits = 1;
        feedbackWithCredit.detailText = 'note';
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

    it('should show the linked-instruction pill from currentFeedback on a new draft', () => {
        fixture.detectChanges();
        const draft = new Feedback();
        draft.gradingInstruction = { id: 1, credits: 2, feedback: 'ok', gradingScale: 'good', instructionDescription: 'desc', usageCount: 0 };
        draft.credits = 2;
        comp.currentFeedback.set(draft);
        fixture.detectChanges();

        const pointsElement = fixture.debugElement.query(By.css('.inline-feedback__points-pill'));
        expect(pointsElement).not.toBeNull();
        expect(pointsElement.nativeElement.textContent).toContain('2P');
        expect(fixture.debugElement.query(By.css('jhi-grading-instruction-link-icon'))).not.toBeNull();
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

        expect(comp['displayTitle']()).toBe('Player');
        expect(fixture.debugElement.query(By.css('.inline-feedback__title'))?.nativeElement.textContent).toBe('Player');
    });

    it('should refresh the title after an in-place instruction link or unlink', () => {
        const instruction = { id: 7, credits: 1, gradingScale: 'good', instructionDescription: 'desc', feedback: 'inst', usageCount: 0 };
        const feedback = { type: FeedbackType.MANUAL, detailText: 'Ok' } as Feedback;
        fixture.componentRef.setInput('gradingCriteria', [{ id: 1, title: 'Player', structuredGradingInstructions: [instruction] }]);
        fixture.componentRef.setInput('feedback', feedback);
        fixture.detectChanges();

        expect(comp['displayTitle']()).toBeUndefined();

        feedback.gradingInstruction = instruction;
        expect(comp['displayTitle']()).toBe('Player');

        feedback.gradingInstruction = undefined;
        expect(comp['displayTitle']()).toBeUndefined();
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
        feedback.detailText = 'note';
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

    it('should disable save without text and allow zero points once text is present', () => {
        const feedback = new Feedback();
        feedback.credits = 0;
        fixture.componentRef.setInput('feedback', feedback);
        comp.editFeedback(codeLine);
        fixture.detectChanges();

        expect((fixture.nativeElement.querySelector('#feedback-save') as HTMLButtonElement).disabled).toBe(true);

        const onUpdateFeedbackSpy = vi.fn();
        comp.onUpdateFeedback.subscribe(onUpdateFeedbackSpy);
        comp.updateFeedback();
        expect(onUpdateFeedbackSpy).not.toHaveBeenCalled();

        const textarea = fixture.nativeElement.querySelector('#feedback-textarea') as HTMLTextAreaElement;
        textarea.value = 'needs a comment';
        textarea.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        expect((fixture.nativeElement.querySelector('#feedback-save') as HTMLButtonElement).disabled).toBe(false);

        comp.updateFeedback();
        expect(onUpdateFeedbackSpy).toHaveBeenCalledWith(expect.objectContaining({ credits: 0, detailText: 'needs a comment' }));
    });

    it('should normalize typed points before saving', () => {
        comp.currentFeedback().detailText = 'note';
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
