import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideTranslateService } from '@ngx-translate/core';

describe('Structured Grading Criteria Service', () => {
    let service: StructuredGradingCriterionService;
    let httpMock: HttpTestingController;
    let feedbacks: Feedback[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
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
    });

    describe('dropping a grading instruction', () => {
        const dropEventWith = (instruction: Partial<GradingInstruction>): Event =>
            ({ preventDefault: () => {}, dataTransfer: { getData: () => JSON.stringify(instruction) } }) as unknown as DragEvent;

        const correct = { id: 1, credits: 2, feedback: 'Bubble Sort is implemented correctly.' };
        const partial = { id: 2, credits: 1, feedback: 'Bubble Sort works in general.' };

        it("should write the criterion's text into the empty description of a tutor's own feedback", () => {
            const feedback = new Feedback();

            service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, dropEventWith(correct));

            expect(feedback.gradingInstruction?.id).toBe(1);
            expect(feedback.credits).toBe(2);
            expect(feedback.detailText).toBe('Bubble Sort is implemented correctly.');
        });

        describe('title', () => {
            // The panel drags the instruction together with its criterion's title (DraggedGradingInstruction)
            const withCriterion = (instruction: Partial<GradingInstruction>) => dropEventWith({ ...instruction, criterionTitle: 'Bubble Sort' });

            it("should name a feedback without a title after the instruction's criterion", () => {
                const feedback = new Feedback();

                service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, withCriterion(correct));

                expect(feedback.text).toBe('Bubble Sort');
                // The criterion's title only names the feedback and is not stored with the instruction
                expect(feedback.gradingInstruction).not.toHaveProperty('criterionTitle');
            });

            it('should replace a points-based default title', () => {
                const feedback = new Feedback();
                feedback.text = 'artemisApp.feedback.type.positive';

                service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, withCriterion(correct));

                expect(feedback.text).toBe('Bubble Sort');
            });

            it('should keep a title the tutor wrote', () => {
                const feedback = new Feedback();
                feedback.text = 'Swap in the inner loop';

                service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, withCriterion(correct));

                expect(feedback.text).toBe('Swap in the inner loop');
            });

            it("should keep an AI suggestion's own title", () => {
                const feedback = new Feedback();
                feedback.text = 'FeedbackSuggestion:accepted:Sorting';

                service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, withCriterion(correct));

                expect(feedback.text).toBe('FeedbackSuggestion:accepted:Sorting');
            });
        });

        it('should replace the text a previously dropped criterion wrote', () => {
            const feedback = new Feedback();
            service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, dropEventWith(correct));

            service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, dropEventWith(partial));

            expect(feedback.detailText).toBe('Bubble Sort works in general.');
        });

        it('should keep a description the tutor wrote', () => {
            const feedback = new Feedback();
            feedback.detailText = 'Check the empty list.';

            service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, dropEventWith(correct));

            expect(feedback.detailText).toBe('Check the empty list.');
        });

        it("should keep the description of an AI suggestion, which the student reads instead of the criterion's text", () => {
            const feedback = new Feedback();
            feedback.text = 'FeedbackSuggestion:accepted:Sorting';
            feedback.detailText = 'Your bubble sort swaps adjacent dates correctly.';

            service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, dropEventWith(correct));

            expect(feedback.gradingInstruction?.id).toBe(1);
            expect(feedback.detailText).toBe('Your bubble sort swaps adjacent dates correctly.');
        });

        it('should not fill the empty description of an AI suggestion either', () => {
            const feedback = new Feedback();
            feedback.text = 'FeedbackSuggestion:accepted:Sorting';

            service.updateFeedbackWithStructuredGradingInstructionEvent(feedback, dropEventWith(correct));

            expect(feedback.detailText).toBeUndefined();
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
