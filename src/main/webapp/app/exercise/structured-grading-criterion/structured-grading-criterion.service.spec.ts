import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

describe('Structured Grading Criteria Service', () => {
    let service: StructuredGradingCriterionService;
    let httpMock: HttpTestingController;
    let feedbacks: Feedback[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(StructuredGradingCriterionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should calculate the total score', () => {
            // define Grading Criteria and Feedback here
            const limitedSGI = new GradingInstruction();
            limitedSGI.id = 1;
            limitedSGI.credits = 1.0;
            limitedSGI.usageCount = 1;
            const unlimitedSGI = new GradingInstruction();
            unlimitedSGI.id = 2;
            unlimitedSGI.credits = 1.0;
            unlimitedSGI.usageCount = 0;
            const bigLimitSGI = new GradingInstruction();
            bigLimitSGI.id = 3;
            bigLimitSGI.credits = 1.0;
            bigLimitSGI.usageCount = 3;

            feedbacks = [];
            feedbacks.push(createFeedback(limitedSGI)); // +1P
            feedbacks.push(createFeedback(limitedSGI)); // +1P will not be counted because limit exceeded
            feedbacks.push(createFeedback(bigLimitSGI)); // +1P
            feedbacks.push(createFeedback(bigLimitSGI)); // +1P will be counted -> limit not exceeded yet
            feedbacks.push(createFeedback(unlimitedSGI)); // +1P
            feedbacks.push(createFeedback(unlimitedSGI)); // +1P

            const returnedFromService = Object.assign([], feedbacks);
            const totalScore = service.computeTotalScore(returnedFromService);
            expect(totalScore).toBe(5.0);
        });

        it('should resolve the criterion title for a grading instruction', () => {
            const title = service.findCriterionTitle(
                [
                    {
                        id: 1,
                        title: 'Player',
                        structuredGradingInstructions: [{ id: 7, credits: 1, gradingScale: 'good', instructionDescription: 'desc', feedback: 'inst', usageCount: 0 }],
                    },
                ],
                7,
            );
            expect(title).toBe('Player');
        });

        it('should calculate the total score too', () => {
            // define Grading Criteria and Feedback here
            const limitedSGI = new GradingInstruction();
            limitedSGI.id = 1;
            limitedSGI.credits = 1.5;
            limitedSGI.usageCount = 1;
            const unlimitedSGI = new GradingInstruction();
            unlimitedSGI.id = 2;
            unlimitedSGI.credits = -0.5;
            unlimitedSGI.usageCount = 0;
            const bigLimitSGI = new GradingInstruction();
            bigLimitSGI.id = 3;
            bigLimitSGI.credits = 1.0;
            bigLimitSGI.usageCount = 3;

            feedbacks = [];
            feedbacks.push(createFeedback(limitedSGI)); // +1.5P
            feedbacks.push(createFeedback(limitedSGI)); // +1.5P will not be counted because limit exceeded
            feedbacks.push(createFeedback(bigLimitSGI)); // +1P
            feedbacks.push(createFeedback(bigLimitSGI)); // +1P will be counted -> limit not exceeded yet
            feedbacks.push(createFeedback(unlimitedSGI)); // -0.5P
            feedbacks.push(createFeedback(unlimitedSGI)); // -0.5P can be applied as often as possible -> unlimited

            const returnedFromService = Object.assign([], feedbacks);
            const totalScore = service.computeTotalScore(returnedFromService);
            expect(totalScore).toBe(2.5);
        });

        it('should apply an armed instruction to feedback without a drop event', () => {
            const selectionService = TestBed.inject(GradingInstructionSelectionService);
            const instruction = new GradingInstruction();
            instruction.id = 9;
            instruction.credits = 2.5;
            selectionService.armInstruction(instruction);

            const feedback = new Feedback();
            feedback.credits = 0;

            expect(service.applyArmedInstructionToFeedback(feedback)).toBe(true);
            expect(feedback.gradingInstruction).toBe(instruction);
            expect(feedback.credits).toBe(2.5);
            expect(selectionService.hasArmedInstruction()).toBe(false);
            expect(service.applyArmedInstructionToFeedback(feedback)).toBe(false);
        });

        it('should not apply or consume an armed instruction when the drop payload is missing', () => {
            const selectionService = TestBed.inject(GradingInstructionSelectionService);
            const instruction = new GradingInstruction();
            instruction.id = 9;
            instruction.credits = 2.5;
            selectionService.armInstruction(instruction);

            const feedback = new Feedback();
            feedback.credits = 0;
            service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, new Event('drop'));

            expect(feedback.gradingInstruction).toBeUndefined();
            expect(feedback.credits).toBe(0);
            expect(selectionService.hasArmedInstruction()).toBe(true);
        });

        it('should apply a dropped instruction from a non-empty text/plain payload', () => {
            const selectionService = TestBed.inject(GradingInstructionSelectionService);
            selectionService.armInstruction({ id: 99, credits: 9 } as GradingInstruction);

            const feedback = new Feedback();
            const event = {
                preventDefault() {},
                dataTransfer: { getData: () => JSON.stringify({ id: 3, credits: 1 }) },
            } as unknown as DragEvent;

            service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, event);

            expect(feedback.gradingInstruction).toEqual({ id: 3, credits: 1 });
            expect(feedback.credits).toBe(1);
            expect(selectionService.hasArmedInstruction()).toBe(true);
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});

function createFeedback(instruction: GradingInstruction) {
    const feedback = new Feedback();
    feedback.gradingInstruction = instruction;
    feedback.credits = instruction.credits;
    return feedback;
}
