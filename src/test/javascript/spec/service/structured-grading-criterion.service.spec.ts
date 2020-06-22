import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import * as chai from 'chai';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { Feedback } from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

const expect = chai.expect;

describe('Structured Grading Criteria Service', () => {
    let injector: TestBed;
    let service: StructuredGradingCriterionService;
    let httpMock: HttpTestingController;
    let expectedResult: any;
    let feedbacks: Feedback[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        expectedResult = {} as HttpResponse<ExerciseHint>;
        injector = getTestBed();
        service = injector.get(StructuredGradingCriterionService);
        httpMock = injector.get(HttpTestingController);

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

        const feedback1 = new Feedback();
        feedback1.gradingInstruction = limitedSGI; // +1P
        feedback1.credits = limitedSGI.credits;
        const feedback2 = new Feedback();
        feedback2.gradingInstruction = limitedSGI; // +1P will not be counted because limit exceeded
        feedback2.credits = limitedSGI.credits;
        const feedback3 = new Feedback();
        feedback3.gradingInstruction = bigLimitSGI; // +1P
        feedback3.credits = bigLimitSGI.credits;
        const feedback4 = new Feedback();
        feedback4.gradingInstruction = bigLimitSGI; // +1P will be counted -> limit not exceeded yet
        feedback4.credits = bigLimitSGI.credits;
        const feedback5 = new Feedback();
        feedback5.gradingInstruction = unlimitedSGI; // +1P
        feedback5.credits = unlimitedSGI.credits;
        const feedback6 = new Feedback();
        feedback6.gradingInstruction = unlimitedSGI; // +1P can be applied as often as possible -> unlimited
        feedback6.credits = unlimitedSGI.credits;

        feedbacks = [];
        feedbacks.push(feedback1);
        feedbacks.push(feedback2);
        feedbacks.push(feedback3);
        feedbacks.push(feedback4);
        feedbacks.push(feedback5);
        feedbacks.push(feedback6);
    });

    describe('Service methods', () => {
        it('should calculate the total score', async () => {
            const returnedFromService = Object.assign([], feedbacks);
            const totalScore = service.computeTotalScore(returnedFromService);
            expect(totalScore).to.deep.equal(5.0);
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
