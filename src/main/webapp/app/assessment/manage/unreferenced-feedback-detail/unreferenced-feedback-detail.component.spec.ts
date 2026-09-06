import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { UnreferencedFeedbackDetailComponent } from 'app/assessment/manage/unreferenced-feedback-detail/unreferenced-feedback-detail.component';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('Unreferenced Feedback Detail Component', () => {
    let comp: UnreferencedFeedbackDetailComponent;
    let fixture: ComponentFixture<UnreferencedFeedbackDetailComponent>;
    let feedbackService: FeedbackService;
    let sgiService: StructuredGradingCriterionService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                MockProvider(StructuredGradingCriterionService),
                MockProvider(FeedbackService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UnreferencedFeedbackDetailComponent);
                comp = fixture.componentInstance;
                feedbackService = TestBed.inject(FeedbackService);
                sgiService = TestBed.inject(StructuredGradingCriterionService);
            });
    });

    it('should render with its required inputs', () => {
        fixture.componentRef.setInput('feedback', { id: 1, detailText: 'some feedback' } as Feedback);
        fixture.componentRef.setInput('resultId', 1);
        fixture.componentRef.setInput('readOnly', false);
        fixture.componentRef.setInput('useDefaultFeedbackSuggestionBadgeText', false);

        expect(() => fixture.detectChanges()).not.toThrow();
    });

    it('should call getLongFeedbackText on init if feedback has long text', async () => {
        const feedbackId = 42;
        const exampleText = 'This is a long feedback text';

        fixture.componentRef.setInput('feedback', { id: feedbackId, hasLongFeedbackText: true } as Feedback);
        fixture.componentRef.setInput('resultId', 1);
        const getLongFeedbackTextSpy = vi.spyOn(feedbackService, 'getLongFeedbackText').mockResolvedValue(exampleText);

        comp.ngOnInit();
        expect(getLongFeedbackTextSpy).toHaveBeenCalledWith(feedbackId);
    });

    it('should update feedback with SGI and emit to parent', () => {
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        const feedback = {
            id: 1,
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback;
        fixture.componentRef.setInput('feedback', feedback);

        // Mock the service to update the feedback with the instruction
        const serviceSpy = vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation((currentFeedback) => {
            currentFeedback.gradingInstruction = instruction;
            currentFeedback.credits = instruction.credits;
        });

        // Spy on the component's output to verify it emits the change
        const emitSpy = vi.spyOn(comp.onFeedbackChange, 'emit');

        comp.updateFeedbackOnDrop(new Event(''));

        // Verify the service was called
        expect(serviceSpy).toHaveBeenCalledOnce();

        // Verify the component emitted the feedback change
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should apply an armed instruction via the dedicated button without a drop event', () => {
        const instruction: GradingInstruction = {
            id: 1,
            credits: 2,
            feedback: 'test',
            gradingScale: 'good',
            instructionDescription: 'description of instruction',
            usageCount: 0,
        };
        const feedback = {
            id: 1,
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback;
        fixture.componentRef.setInput('feedback', feedback);
        fixture.componentRef.setInput('resultId', 1);
        fixture.componentRef.setInput('readOnly', false);
        fixture.componentRef.setInput('useDefaultFeedbackSuggestionBadgeText', false);

        TestBed.inject(GradingInstructionSelectionService).armInstruction(instruction);

        const applySpy = vi.spyOn(sgiService, 'applyArmedInstructionToFeedback').mockImplementation((currentFeedback) => {
            currentFeedback.gradingInstruction = instruction;
            currentFeedback.credits = instruction.credits;
            return true;
        });
        const dropSpy = vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent');
        const emitSpy = vi.spyOn(comp.onFeedbackChange, 'emit');

        comp.applyArmedInstruction();

        expect(applySpy).toHaveBeenCalledWith(feedback);
        expect(dropSpy).not.toHaveBeenCalled();
        expect(feedback.gradingInstruction).toEqual(instruction);
        expect(feedback.credits).toBe(2);
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should emit the assessment change after deletion', () => {
        fixture.componentRef.setInput('feedback', {
            id: 1,
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback);
        const emitSpy = vi.spyOn(comp.onFeedbackDelete, 'emit');
        comp.delete();

        expect(emitSpy).toHaveBeenCalledTimes(1);
    });

    it('should mark automatic feedback and feedback suggestions as adapted when they are modified', () => {
        fixture.componentRef.setInput('feedback', {
            id: 1,
            type: FeedbackType.AUTOMATIC,
            text: 'FeedbackSuggestion:accepted:feedback1',
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback);
        const emitSpy = vi.spyOn(comp.onFeedbackChange, 'emit');
        comp.emitChanges();
        expect(emitSpy).toHaveBeenCalledWith({
            id: 1,
            type: FeedbackType.AUTOMATIC_ADAPTED,
            text: 'FeedbackSuggestion:adapted:feedback1',
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback);
    });

    it('should preserve suggestion prefix when updating AI title', () => {
        fixture.componentRef.setInput('feedback', {
            id: 1,
            type: FeedbackType.AUTOMATIC,
            text: 'FeedbackSuggestion:Model quality',
            detailText: 'Improve the diagram',
            credits: 1,
        } as Feedback);
        const emitSpy = vi.spyOn(comp.onFeedbackChange, 'emit');
        comp.updateHeaderTitle('Updated title');
        expect(emitSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                text: 'FeedbackSuggestion:Updated title',
                detailText: 'Improve the diagram',
            }),
        );
    });

    it('should store manual header in feedback text', () => {
        fixture.componentRef.setInput('feedback', {
            id: 1,
            detailText: 'Body',
            credits: 1,
        } as Feedback);
        const emitSpy = vi.spyOn(comp.onFeedbackChange, 'emit');
        comp.updateHeaderTitle('Player');
        expect(emitSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                text: 'Player',
                detailText: 'Body',
            }),
        );
    });

    it('should update tone when credits change via the stepper', () => {
        const originalFeedback = {
            id: 1,
            detailText: 'feedback',
            credits: 0.5,
        } as Feedback;
        fixture.componentRef.setInput('feedback', originalFeedback);
        fixture.componentRef.setInput('readOnly', false);
        fixture.componentRef.setInput('resultId', 1);
        fixture.componentRef.setInput('useDefaultFeedbackSuggestionBadgeText', false);
        const emitSpy = vi.spyOn(comp.onFeedbackChange, 'emit');
        fixture.detectChanges();

        const card = () => fixture.nativeElement.querySelector('tum-ui-card') as HTMLElement;
        expect(card().getAttribute('data-tone')).toBe('positive');

        comp.stepCredits(-comp.CREDITS_STEP);
        fixture.detectChanges();
        expect(comp.feedback()).toBe(originalFeedback);
        expect(comp.feedback().credits).toBe(0);
        expect(card().getAttribute('data-tone')).toBe('neutral');
        expect(emitSpy).toHaveBeenCalledWith(originalFeedback);

        comp.stepCredits(-comp.CREDITS_STEP);
        fixture.detectChanges();
        expect(comp.feedback().credits).toBe(-0.5);
        expect(card().getAttribute('data-tone')).toBe('negative');
    });

    it('should normalize typed credits before emitting feedback', () => {
        const originalFeedback = { credits: 0 } as Feedback;
        fixture.componentRef.setInput('feedback', originalFeedback);
        const emitSpy = vi.spyOn(comp.onFeedbackChange, 'emit');

        comp.updateCredits(0.3);

        expect(comp.feedback()).toBe(originalFeedback);
        expect(comp.feedback().credits).toBe(0.5);
        expect(emitSpy).toHaveBeenCalledWith(originalFeedback);
    });

    it('should give each card unique control ids linked to Title and Feedback labels', () => {
        fixture.componentRef.setInput('feedback', { detailText: 'note', credits: 1 } as Feedback);
        fixture.componentRef.setInput('readOnly', false);
        fixture.componentRef.setInput('resultId', 1);
        fixture.componentRef.setInput('useDefaultFeedbackSuggestionBadgeText', false);
        fixture.detectChanges();

        const header = fixture.nativeElement.querySelector('.feedback-card__header-input') as HTMLInputElement;
        const textarea = fixture.nativeElement.querySelector('.feedback-card__textarea') as HTMLTextAreaElement;
        const points = fixture.nativeElement.querySelector('.feedback-card__points-input') as HTMLInputElement;
        expect(header?.id).toBeTruthy();
        expect(textarea?.id).toBeTruthy();
        expect(points?.id).toBeTruthy();
        expect(fixture.nativeElement.querySelector(`label[for="${header.id}"]`)).not.toBeNull();
        expect(fixture.nativeElement.querySelector(`label[for="${textarea.id}"]`)).not.toBeNull();
        expect(new Set([header.id, textarea.id, points.id]).size).toBe(3);
    });
});
