import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockProvider } from 'ng-mocks';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { UnreferencedFeedbackDetailComponent } from 'app/assessment/manage/unreferenced-feedback-detail/unreferenced-feedback-detail.component';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Unreferenced Feedback Detail Component', () => {
    setupTestBed({ zoneless: true });
    let comp: UnreferencedFeedbackDetailComponent;
    let fixture: ComponentFixture<UnreferencedFeedbackDetailComponent>;
    let feedbackService: FeedbackService;
    let sgiService: StructuredGradingCriterionService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(StructuredGradingCriterionService), MockProvider(FeedbackService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UnreferencedFeedbackDetailComponent);
                comp = fixture.componentInstance;
                feedbackService = TestBed.inject(FeedbackService);
                sgiService = TestBed.inject(StructuredGradingCriterionService);
            });
    });

    it('should call getLongFeedbackText on init if feedback has long text', async () => {
        const feedbackId = 42;
        const resultId = 1;
        const exampleText = 'This is a long feedback text';

        fixture.componentRef.setInput('feedback', { id: feedbackId, hasLongFeedbackText: true } as Feedback);
        fixture.componentRef.setInput('resultId', resultId);
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
});
