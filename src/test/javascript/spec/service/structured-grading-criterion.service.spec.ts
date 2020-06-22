import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as chai from 'chai';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { Feedback } from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

const expect = chai.expect;

describe('Structured Grading Criteria Service', () => {
    let injector: TestBed;
    let service: StructuredGradingCriterionService;
    let httpMock: HttpTestingController;
    let feedbacks: Feedback[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(StructuredGradingCriterionService);
        httpMock = injector.get(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should calculate the total score', async () => {
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
            feedbacks.push(createFeedbacK(limitedSGI)); // +1P
            feedbacks.push(createFeedbacK(limitedSGI)); // +1P will not be counted because limit exceeded
            feedbacks.push(createFeedbacK(bigLimitSGI)); // +1P
            feedbacks.push(createFeedbacK(bigLimitSGI)); // +1P will be counted -> limit not exceeded yet
            feedbacks.push(createFeedbacK(unlimitedSGI)); // +1P
            feedbacks.push(createFeedbacK(unlimitedSGI)); // +1P

            const returnedFromService = Object.assign([], feedbacks);
            const totalScore = service.computeTotalScore(returnedFromService);
            expect(totalScore).to.deep.equal(5.0);
        });
        it('should calculate the total score', async () => {
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
            feedbacks.push(createFeedbacK(limitedSGI)); // +1.5P
            feedbacks.push(createFeedbacK(limitedSGI)); // +1.5P will not be counted because limit exceeded
            feedbacks.push(createFeedbacK(bigLimitSGI)); // +1P
            feedbacks.push(createFeedbacK(bigLimitSGI)); // +1P will be counted -> limit not exceeded yet
            feedbacks.push(createFeedbacK(unlimitedSGI)); // -0.5P
            feedbacks.push(createFeedbacK(unlimitedSGI)); // -0.5P can be applied as often as possible -> unlimited

            const returnedFromService = Object.assign([], feedbacks);
            const totalScore = service.computeTotalScore(returnedFromService);
            expect(totalScore).to.deep.equal(2.5);
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});

function createFeedbacK(instr: GradingInstruction) {
    const feedback = new Feedback();
    feedback.gradingInstruction = instr;
    feedback.credits = instr.credits;
    return feedback;
}
