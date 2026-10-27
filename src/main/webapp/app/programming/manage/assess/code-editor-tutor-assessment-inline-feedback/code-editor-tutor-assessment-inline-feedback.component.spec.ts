import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { signal } from '@angular/core';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from './code-editor-tutor-assessment-inline-feedback.component';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { By } from '@angular/platform-browser';

describe('CodeEditorTutorAssessmentInlineFeedbackComponent', () => {
    let comp: CodeEditorTutorAssessmentInlineFeedbackComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    let selection: GradingInstructionSelectionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CodeEditorTutorAssessmentInlineFeedbackComponent],
            providers: [
                { provide: DeleteDialogService, useValue: { openDeleteDialog: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(CodeEditorTutorAssessmentInlineFeedbackComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('readOnly', false);
        fixture.componentRef.setInput('selectedFile', 'testFile');
        fixture.componentRef.setInput('codeLine', 1);
        selection = TestBed.inject(GradingInstructionSelectionService);
    });
    afterEach(() => vi.restoreAllMocks());

    async function settle() {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    function savedFeedback(): Feedback {
        return { id: 1, text: 'File testFile at line 2', detailText: 'Original comment', credits: 1, type: FeedbackType.MANUAL, reference: 'file:testFile_line:1' };
    }

    async function open(feedback: Feedback) {
        fixture.componentRef.setInput('feedback', feedback);
        await settle();
        comp.editFeedback(1);
        await settle();
    }

    async function typeComment(value: string) {
        const textarea = fixture.nativeElement.querySelector('[data-testid="feedback-detail"]') as HTMLTextAreaElement;
        textarea.value = value;
        textarea.dispatchEvent(new Event('input'));
        await settle();
    }

    it('uses the shared card and commits a new feedback only on Save', async () => {
        const updated = vi.fn();
        comp.onUpdateFeedback.subscribe(updated);
        await settle();
        await typeComment('New comment');
        const shared = fixture.debugElement.query(By.directive(UnifiedFeedbackComponent)).componentInstance as UnifiedFeedbackComponent;
        shared.onCreditsChange(1.5);
        await settle();
        expect(comp.hasUnsavedEdits()).toBe(true);
        expect(updated).not.toHaveBeenCalled();
        fixture.nativeElement.querySelector('[data-testid="feedback-save"]').click();
        await settle();
        expect(updated).toHaveBeenCalledExactlyOnceWith(
            expect.objectContaining({ detailText: 'New comment', credits: 1.5, type: FeedbackType.MANUAL, reference: 'file:testFile_line:1', positive: true }),
        );
        expect(comp.viewOnly()).toBe(true);
    });

    it('blocks empty text and blank points immediately', async () => {
        await settle();
        const save = () => fixture.nativeElement.querySelector('[data-testid="feedback-save"]') as HTMLButtonElement;
        expect(save().disabled).toBe(true);
        await typeComment('Comment');
        const shared = fixture.debugElement.query(By.directive(UnifiedFeedbackComponent)).componentInstance as UnifiedFeedbackComponent;
        shared.onCreditsChange(2);
        await settle();
        expect(save().disabled).toBe(false);
        shared.onCreditsChange(undefined);
        await settle();
        expect(save().disabled).toBe(true);
    });

    it('keeps saved feedback unchanged until Save and discards local edits on Cancel', async () => {
        const feedback = savedFeedback();
        const updated = vi.fn();
        comp.onUpdateFeedback.subscribe(updated);
        await open(feedback);
        await typeComment('Unsaved edit');
        expect(feedback.detailText).toBe('Original comment');
        expect(comp.hasUnsavedEdits()).toBe(true);
        fixture.nativeElement.querySelector('[data-testid="feedback-cancel"]').click();
        await settle();
        expect(comp.currentFeedback().detailText).toBe('Original comment');
        expect(comp.hasUnsavedEdits()).toBe(false);
        expect(updated).not.toHaveBeenCalled();
    });

    it('retains local comment and points when the parent rebinds unchanged feedback', async () => {
        const feedback = savedFeedback();
        await open(feedback);
        await typeComment('Edited comment');
        comp.currentFeedback().credits = -2;
        fixture.componentRef.setInput('feedback', { ...feedback });
        await settle();
        expect(comp.currentFeedback()).toMatchObject({ detailText: 'Edited comment', credits: -2 });
        comp.updateFeedback();
        expect(comp.currentFeedback()).toMatchObject({ detailText: 'Edited comment', credits: -2 });
    });

    it('reserves the original instruction through unlink, apply elsewhere and Cancel', async () => {
        const instruction = { id: 7, credits: 1, usageCount: 1, feedback: 'Rubric feedback' } as GradingInstruction;
        const applyElsewhere = vi.fn();
        selection.register({
            appliedInstructionIds: signal(new Set([7])),
            appliedInstructionCounts: signal(new Map([[7, 1]])),
            removableInstructionIds: signal(new Set()),
            applyInstruction: applyElsewhere,
            unapplyInstruction: vi.fn(),
            unapplyOneInstruction: vi.fn(),
        });
        const feedback = savedFeedback();
        feedback.gradingInstruction = instruction;
        await open(feedback);
        comp.currentFeedback().gradingInstruction = undefined;
        const shared = fixture.debugElement.query(By.directive(UnifiedFeedbackComponent)).componentInstance as UnifiedFeedbackComponent;
        shared.onInstructionUnlinked.emit();
        selection.addApplication(instruction);
        expect(applyElsewhere).not.toHaveBeenCalled();
        expect(selection.applicationCount(instruction)).toBe(1);
        comp.cancelFeedback();
        expect(comp.currentFeedback().gradingInstruction?.id).toBe(7);
        expect(selection.applicationCount(instruction)).toBe(1);
    });

    it('links an accepted suggestion on a detached copy and keeps its identity until Save', async () => {
        const feedback = savedFeedback();
        feedback.id = undefined;
        feedback.text = `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Suggestion title`;
        const updated = vi.fn();
        comp.onUpdateFeedback.subscribe(updated);
        await open(feedback);
        selection.toggleArmedInstruction({ id: 7, credits: 2, feedback: 'Instruction feedback' } as GradingInstruction);
        comp.applyArmedInstruction();
        await settle();
        expect(feedback.text).toBe(`${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Suggestion title`);
        expect(feedback.gradingInstruction).toBeUndefined();
        expect(comp.currentFeedback().text).toBe(`${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Suggestion title`);
        expect(updated).not.toHaveBeenCalled();
        comp.cancelFeedback();
        expect(comp.currentFeedback()).toEqual(feedback);
    });

    it('keeps a new linked draft counted until it is canceled', async () => {
        const instruction = { id: 7, credits: 1, usageCount: 1, feedback: 'Rubric feedback' } as GradingInstruction;
        selection.register({
            appliedInstructionIds: signal(new Set()),
            appliedInstructionCounts: signal(new Map()),
            removableInstructionIds: signal(new Set()),
            applyInstruction: vi.fn(),
            unapplyInstruction: vi.fn(),
            unapplyOneInstruction: vi.fn(),
        });
        await settle();
        selection.toggleArmedInstruction(instruction);
        comp.applyArmedInstruction();
        expect(selection.applicationCount(instruction)).toBe(1);
        expect(selection.canApplyInstruction(instruction)).toBe(false);
        comp.cancelFeedback();
        expect(selection.applicationCount(instruction)).toBe(0);
    });

    it('reports raw point edits even before a partial negative value reaches the model', async () => {
        await open(savedFeedback());
        const input = fixture.nativeElement.querySelector('jhi-points-input input') as HTMLInputElement;
        input.value = '-';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        await settle();
        expect(comp.hasUnsavedEdits()).toBe(true);
        comp.cancelFeedback();
        expect(comp.hasUnsavedEdits()).toBe(false);
    });

    it('opens a detached edit session when an instruction is dropped onto a collapsed card', async () => {
        const feedback = savedFeedback();
        fixture.componentRef.setInput('feedback', feedback);
        await settle();
        const instruction = { id: 7, credits: 2, feedback: 'Rubric feedback' };
        const event = { preventDefault: vi.fn(), dataTransfer: { getData: () => JSON.stringify(instruction) } } as unknown as Event;
        comp.updateFeedbackOnDrop(event);
        await settle();
        expect(comp.viewOnly()).toBe(false);
        expect(comp.currentFeedback().gradingInstruction?.id).toBe(7);
        expect(feedback.gradingInstruction).toBeUndefined();
        expect(comp.hasUnsavedEdits()).toBe(true);
        comp.cancelFeedback();
        expect(comp.currentFeedback()).toEqual(feedback);
    });

    it('preserves partial negative entry and steps off-grid values from the raw input', async () => {
        await settle();
        const input = fixture.nativeElement.querySelector('jhi-points-input input') as HTMLInputElement;
        for (const value of ['-', '-2']) {
            input.value = value;
            input.dispatchEvent(new Event('input'));
            await settle();
            expect(input.value).toBe(value);
        }
        input.value = '1.3';
        input.dispatchEvent(new Event('input'));
        await settle();
        const increment = fixture.nativeElement.querySelector('[data-testid="increase-feedback-points"]') as HTMLButtonElement;
        const down = new MouseEvent('mousedown', { cancelable: true });
        increment.dispatchEvent(down);
        expect(down.defaultPrevented).toBe(true);
        increment.click();
        await settle();
        expect(comp.currentFeedback().credits).toBe(1.5);
        await fixture.whenStable();
        fixture.detectChanges();
        expect(input.value).toBe('1.5');
    });

    it('renders linked criteria and student-facing feedback without the hidden old title', async () => {
        const feedback = savedFeedback();
        feedback.text = 'Hidden old title';
        feedback.gradingInstruction = { id: 7, credits: 1, feedback: 'Student-facing text' } as GradingInstruction;
        fixture.componentRef.setInput('gradingCriteria', [{ title: 'Criterion title', structuredGradingInstructions: [feedback.gradingInstruction] }]);
        fixture.componentRef.setInput('feedback', feedback);
        await settle();
        expect(fixture.nativeElement.textContent).toContain('Criterion title');
        expect(fixture.nativeElement.querySelector('[data-testid="unified-feedback-text"]').textContent).toContain('Student-facing text');
        expect(fixture.nativeElement.textContent).not.toContain('Hidden old title');
    });

    it('does not offer mutation controls in read-only mode', async () => {
        fixture.componentRef.setInput('readOnly', true);
        fixture.componentRef.setInput('feedback', savedFeedback());
        await settle();
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
        const update = vi.fn();
        comp.onUpdateFeedback.subscribe(update);
        comp.updateFeedback();
        comp.editFeedback(1);
        expect(update).not.toHaveBeenCalled();
        expect(comp.viewOnly()).toBe(true);
    });

    it('ignores a drop without instruction data', () => {
        const update = vi.spyOn(TestBed.inject(StructuredGradingCriterionService), 'updateFeedbackWithStructuredGradingInstructionEvent');
        const event = new Event('drop');
        comp.updateFeedbackOnDrop(event);
        expect(update).toHaveBeenCalledWith(comp.currentFeedback(), event);
    });
});
