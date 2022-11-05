import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { Feedback } from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

describe('Structured Grading Criteria Service', () => {
    let service: StructuredGradingCriterionService;
    let httpMock: HttpTestingController;
    let feedbacks: Feedback[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(StructuredGradingCriterionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should calculate the total score', fakeAsync(() => {
            // define Grading Criteria and Feedback here
            const limitedSGI = new GradingInstruction();
            limitedSGI.id = 1;
            limitedSGI.credits = 1.0;
            limitedSGI.usageCount = 1;
            const unlimitedSGI = new GradingInstruction();
            limitedSGI.id = 2;
            unlimitedSGI.credits = 1.0;
            unlimitedSGI.usageCount = 0;
            const bigLimitSGI = new GradingInstruction();
            limitedSGI.id = 3;
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
            tick();
        }));
        it('should calculate the total score too', fakeAsync(() => {
            // define Grading Criteria and Feedback here
            const limitedSGI = new GradingInstruction();
            limitedSGI.id = 1;
            limitedSGI.credits = 1.5;
            limitedSGI.usageCount = 1;
            const unlimitedSGI = new GradingInstruction();
            limitedSGI.id = 2;
            unlimitedSGI.credits = -0.5;
            unlimitedSGI.usageCount = 0;
            const bigLimitSGI = new GradingInstruction();
            limitedSGI.id = 3;
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
            tick();
        }));
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
