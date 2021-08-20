import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { OrionExerciseAssessmentDashboardComponent } from 'app/orion/assessment/orion-exercise-assessment-dashboard.component';
import { spy, stub } from 'sinon';
import { ExerciseType } from 'app/entities/exercise.model';
import { TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OrionState } from 'app/shared/orion/orion';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrionExerciseAssessmentDashboardComponent', () => {
    let comp: OrionExerciseAssessmentDashboardComponent;
    let orionConnectorService: OrionConnectorService;

    const programmingExercise = {
        id: 16,
        type: ExerciseType.PROGRAMMING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as ProgrammingExercise;
    const programmingSubmission = { id: 11, participation: { id: 1 } } as any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                OrionExerciseAssessmentDashboardComponent,
                MockComponent(ExerciseAssessmentDashboardComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(OrionButtonComponent),
            ],
            providers: [
                MockProvider(OrionConnectorService),
                MockProvider(OrionAssessmentService),
                MockProvider(ExerciseService),
                MockProvider(JhiAlertService),
                MockProvider(TranslateService),
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ exerciseId: 10 }) } } },
            ],
        })
            .compileComponents()
            .then(() => {
                comp = TestBed.createComponent(OrionExerciseAssessmentDashboardComponent).componentInstance;
                orionConnectorService = TestBed.inject(OrionConnectorService);
                comp.exerciseId = programmingExercise.id!;
                comp.exercise = programmingExercise;
            });
    });
    afterEach(() => {
        sinon.restore();
    });

    it('openAssessmentInOrion should call connector', () => {
        const assessExerciseSpy = spy(orionConnectorService, 'assessExercise');

        comp.openAssessmentInOrion();

        expect(assessExerciseSpy).to.have.been.calledOnceWithExactly(programmingExercise);
    });
    it('downloadSubmissionInOrion should call service', () => {
        const downloadSubmissionSpy = spy(TestBed.inject(OrionAssessmentService), 'downloadSubmissionInOrion');

        comp.downloadSubmissionInOrion(programmingSubmission, 2);

        expect(downloadSubmissionSpy).to.have.been.calledOnceWithExactly(comp.exerciseId, programmingSubmission, 2);
    });
    it('ngOnInit should subscribe correctly', fakeAsync(() => {
        const orionState = { opened: 40, building: false, cloning: false } as OrionState;
        const stateObservable = new BehaviorSubject(orionState);
        const orionStateStub = stub(orionConnectorService, 'state');
        orionStateStub.returns(stateObservable);

        const response = of(new HttpResponse({ body: programmingExercise, status: 200 }));
        const getForTutorsStub = stub(TestBed.inject(ExerciseService), 'getForTutors');
        getForTutorsStub.returns(response);

        comp.ngOnInit();
        tick();

        expect(comp.exerciseId).to.be.equals(10);
        expect(comp.exercise).to.be.deep.equals(programmingExercise);
        expect(comp.orionState).to.be.deep.equals(orionState);

        expect(getForTutorsStub).to.have.been.calledOnceWithExactly(10);
        expect(orionStateStub).to.have.been.calledOnceWithExactly();
    }));
    it('ngOnInit should deal with error correctly', fakeAsync(() => {
        const orionState = { opened: 40, building: false, cloning: false } as OrionState;
        const stateObservable = new BehaviorSubject(orionState);
        const orionStateStub = stub(orionConnectorService, 'state');
        orionStateStub.returns(stateObservable);

        const error = new HttpErrorResponse({ status: 400 });
        const getForTutorsStub = stub(TestBed.inject(ExerciseService), 'getForTutors');
        getForTutorsStub.returns(throwError(error));

        const errorSpy = spy(TestBed.inject(JhiAlertService), 'error');
        // counter the initialization in beforeEach
        comp.exercise = undefined as any;

        comp.ngOnInit();
        tick();

        expect(comp.exerciseId).to.be.equals(10);
        expect(comp.exercise).to.be.deep.equals(undefined);
        expect(comp.orionState).to.be.deep.equals(orionState);

        expect(getForTutorsStub).to.have.been.calledOnceWithExactly(10);
        expect(orionStateStub).to.have.been.calledOnceWithExactly();
        expect(errorSpy).to.have.been.calledOnceWithExactly('error.http.400');
    }));
});
