import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SinonStub, stub } from 'sinon';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { FeedbackItem, FeedbackItemType, ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

chai.use(sinonChai);
const expect = chai.expect;

describe('ResultDetailComponent', () => {
    let comp: ResultDetailComponent;
    let fixture: ComponentFixture<ResultDetailComponent>;
    let debugElement: DebugElement;

    let buildLogService: BuildLogService;
    let resultService: ResultService;
    let buildlogsStub: SinonStub;
    let getFeedbackDetailsForResultStub: SinonStub;

    const makeFeedback = (fb: Feedback) => {
        return Object.assign({ type: FeedbackType.AUTOMATIC, text: '', detailText: '', credits: 0 } as Feedback, fb);
    };

    const makeFeedbackItem = (item: FeedbackItem) => {
        return Object.assign({ type: FeedbackItemType.Feedback, credits: 0, title: undefined, positive: undefined } as FeedbackItem, item);
    };

    const generateSCAFeedbackPair = () => {
        return {
            fb: makeFeedback({
                text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + 'Bad Practice',
                detailText: JSON.stringify({
                    filePath: 'www/withSCA/MergeSort.java',
                    startLine: 9,
                    rule: 'Rule1',
                    message: 'This is bad practice',
                }),
                credits: -2,
                positive: false,
            }),
            item: makeFeedbackItem({
                type: FeedbackItemType.Issue,
                category: 'Code Issue',
                title: 'Bad Practice Issue in file www/withSCA/MergeSort.java at line 9',
                text: 'Rule1: This is bad practice',
                credits: -2,
                positive: false,
            }),
        };
    };

    const generateTestCaseFeedbackPair = () => {
        return {
            fb: makeFeedback({
                text: 'TestCase1',
                detailText: 'This failed.',
            }),
            item: makeFeedbackItem({
                type: FeedbackItemType.Test,
                category: 'Feedback',
                text: 'This failed.',
            }),
        };
    };

    const generateFeedbacksAndExpectedItems = () => {
        const feedbacks: Feedback[] = [];
        const expectedItems: FeedbackItem[] = [];
        const addPair = (pair: { fb: Feedback; item: FeedbackItem }) => {
            feedbacks.push(pair.fb);
            expectedItems.push(pair.item);
        };
        addPair(generateSCAFeedbackPair());
        addPair(generateTestCaseFeedbackPair());
        return { feedbacks, expectedItems };
    };

    const generateProgrammingSubmission = (buildFailed: boolean) => {
        const programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.buildFailed = buildFailed;
        return programmingSubmission;
    };

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, ArtemisResultModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultDetailComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                comp.result = { id: 89, participation: { id: 55 } } as Result;

                buildLogService = debugElement.injector.get(BuildLogService);
                resultService = debugElement.injector.get(ResultService);

                buildlogsStub = stub(buildLogService, 'getBuildLogs').returns(of([]));
                getFeedbackDetailsForResultStub = stub(resultService, 'getFeedbackDetailsForResult').returns(of({ body: [] as Feedback[] } as HttpResponse<Feedback[]>));
            });
    });

    it('should not try to retrieve the feedbacks from the server if provided result has feedbacks', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.not.have.been.called;
        expect(comp.feedbackList).to.have.deep.members(expectedItems);
        expect(comp.isLoading).to.be.false;
    });

    it('should try to retrieve the feedbacks from the server if provided result does not have feedbacks', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        getFeedbackDetailsForResultStub.returns(of({ body: feedbacks } as HttpResponse<Feedback[]>));

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(comp.result.id);
        expect(comp.feedbackList).to.have.same.deep.members(expectedItems);
        expect(comp.isLoading).to.be.false;
    });

    it('should try to retrieve build logs if the exercise type is PROGRAMMING and no submission was provided.', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;

        comp.ngOnInit();

        expect(buildlogsStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id);
        expect(comp.buildLogs).to.deep.equal([]);
        expect(comp.isLoading).to.be.false;
    });

    it('should try to retrieve build logs if the exercise type is PROGRAMMING and a submission was provided which was marked with build failed.', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.submission = generateProgrammingSubmission(true);

        comp.ngOnInit();

        expect(buildlogsStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id);
        expect(comp.buildLogs).to.deep.equal([]);
        expect(comp.isLoading).to.be.false;
    });

    it('should not try to retrieve build logs if the exercise type is not PROGRAMMING', () => {
        comp.exerciseType = ExerciseType.MODELING;
        comp.result.submission = new ModelingSubmission();

        comp.ngOnInit();

        expect(buildlogsStub).to.not.have.been.called;
        expect(comp.feedbackList).to.be.undefined;
        expect(comp.isLoading).to.be.false;
    });

    it('should not try to retrieve build logs if submission was not marked with build failed', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.submission = generateProgrammingSubmission(false);

        comp.ngOnInit();

        expect(buildlogsStub).to.not.have.been.called;
        expect(comp.buildLogs).to.be.undefined;
        expect(comp.isLoading).to.be.false;
    });

    it('fetchBuildLogs should suppress 403 error', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        const response = new HttpErrorResponse({ status: 403 });
        buildlogsStub.returns(throwError(response));

        comp.ngOnInit();

        expect(buildlogsStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id);
        expect(comp.loadingFailed).to.be.false;
        expect(comp.isLoading).to.be.false;
    });

    it('fetchBuildLogs should not suppress errors with status other than 403', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        const response = new HttpErrorResponse({ status: 500 });
        buildlogsStub.returns(throwError(response));

        comp.ngOnInit();

        expect(buildlogsStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id);
        expect(comp.loadingFailed).to.be.true;
        expect(comp.isLoading).to.be.false;
    });
});
