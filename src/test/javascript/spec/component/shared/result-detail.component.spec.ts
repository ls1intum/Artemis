import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RepositoryService } from 'app/exercises/shared/result/repository.service';
import { SinonStub, stub } from 'sinon';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ResultDetailComponent', () => {
    let comp: ResultDetailComponent;
    let fixture: ComponentFixture<ResultDetailComponent>;
    let debugElement: DebugElement;

    let repositoryService: RepositoryService;
    let resultService: ResultService;
    let buildlogsStub: SinonStub;
    let getFeedbackDetailsForResultStub: SinonStub;

    const generateSCAFeedback = () => {
        const scaFeedback = new Feedback();
        scaFeedback.id = 42;
        scaFeedback.type = FeedbackType.AUTOMATIC;
        scaFeedback.text = STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + 'test';
        return scaFeedback;
    };

    const generateTestCaseFeedback = () => {
        const tcFeedback = new Feedback();
        tcFeedback.id = 55;
        tcFeedback.type = FeedbackType.AUTOMATIC;
        return tcFeedback;
    };

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, ArtemisResultModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultDetailComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                repositoryService = debugElement.injector.get(RepositoryService);
                resultService = debugElement.injector.get(ResultService);

                buildlogsStub = stub(repositoryService, 'buildlogs').returns(of([]));
                getFeedbackDetailsForResultStub = stub(resultService, 'getFeedbackDetailsForResult').returns(of({ body: [] as Feedback[] } as HttpResponse<Feedback[]>));
            });
    });

    it('should not try to retrieve the feedbacks from the server if provided result has results', () => {
        const feedbacks: Feedback[] = [generateSCAFeedback(), generateTestCaseFeedback()];
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result = { id: 89, participation: { id: 55 }, feedbacks } as Result;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.not.have.been.called;
        expect(buildlogsStub).to.not.have.been.called;
        expect([...comp.feedbackList, ...comp.staticCodeAnalysisFeedbackList]).to.have.same.deep.members(feedbacks);
        expect(comp.buildLogs).to.be.undefined;
        expect(comp.isLoading).to.be.false;
    });

    it('should try to retrieve the feedbacks from the server if provided result does not have results', () => {
        const feedbacks: Feedback[] = [generateSCAFeedback(), generateTestCaseFeedback()];
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result = { id: 89, participation: { id: 55 } } as Result;
        getFeedbackDetailsForResultStub.returns(of({ body: feedbacks as Feedback[] } as HttpResponse<Feedback[]>));

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(comp.result.id);
        expect(buildlogsStub).to.not.have.been.called;
        expect([...comp.feedbackList, ...comp.staticCodeAnalysisFeedbackList]).to.have.same.deep.members(feedbacks);
        expect(comp.buildLogs).to.be.undefined;
        expect(comp.isLoading).to.be.false;
    });

    it('should split static code analysis feedback and test case feedback correctly', () => {
        const scaFeedback = generateSCAFeedback();
        const testCaseFeedback = generateTestCaseFeedback();
        const feedbacks: Feedback[] = [scaFeedback, testCaseFeedback];
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result = { id: 89, participation: { id: 55 }, feedbacks } as Result;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.not.have.been.called;
        expect(buildlogsStub).to.not.have.been.called;
        expect(comp.staticCodeAnalysisFeedbackList).to.have.same.deep.members([scaFeedback]);
        expect(comp.feedbackList).to.have.same.deep.members([testCaseFeedback]);
    });

    it('should try to retrieve build logs if the exercise type is PROGRAMMING and no feedbacks are provided.', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result = { id: 89, participation: { id: 55 } } as Result;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(comp.result.id);
        expect(buildlogsStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id);
        expect(comp.feedbackList).to.be.undefined;
        expect(comp.buildLogs).to.deep.equal([]);
        expect(comp.isLoading).to.be.false;
    });

    it('should not try to retrieve build logs if the exercise type is not PROGRAMMING and no feedbacks are provided.', () => {
        comp.exerciseType = ExerciseType.MODELING;
        comp.result = { id: 89, participation: { id: 55 } } as Result;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(comp.result.id);
        expect(buildlogsStub).to.not.have.been.called;
        expect(comp.feedbackList).to.be.undefined;
        expect(comp.buildLogs).to.be.undefined;
        expect(comp.isLoading).to.be.false;
    });
});
