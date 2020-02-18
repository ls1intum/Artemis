import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RepositoryService } from 'app/entities/repository/repository.service';
import { SinonStub, stub } from 'sinon';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Feedback } from 'app/entities/feedback/feedback.model';
import { ResultService } from 'app/entities/result/result.service';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { ResultDetailComponent } from 'app/entities/result/result-detail.component';
import { ExerciseType } from 'app/entities/exercise/exercise.model';
import { Result } from 'app/entities/result/result.model';

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
        const feedbacks = [{ id: 55 }];
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result = { id: 89, participation: { id: 55 }, feedbacks } as Result;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.not.have.been.called;
        expect(buildlogsStub).to.not.have.been.called;
        expect(comp.feedbackList).to.be.deep.equal(feedbacks);
        expect(comp.buildLogs).to.be.undefined;
        expect(comp.isLoading).to.be.false;
    });

    it('should try to retrieve the feedbacks from the server if provided result does not have results', () => {
        const feedbacks = [{ id: 55 }];
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result = { id: 89, participation: { id: 55 } } as Result;
        getFeedbackDetailsForResultStub.returns(of({ body: feedbacks as Feedback[] } as HttpResponse<Feedback[]>));

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(comp.result.id);
        expect(buildlogsStub).to.not.have.been.called;
        expect(comp.feedbackList).to.be.deep.equal(feedbacks);
        expect(comp.buildLogs).to.be.undefined;
        expect(comp.isLoading).to.be.false;
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
