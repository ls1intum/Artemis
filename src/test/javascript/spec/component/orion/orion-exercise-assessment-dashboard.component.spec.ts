import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { OrionExerciseAssessmentDashboardComponent } from 'app/orion/assessment/orion-exercise-assessment-dashboard.component';
import { SinonStub, spy, stub } from 'sinon';
import { ExerciseType } from 'app/entities/exercise.model';
import { TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingAssessmentRepoExportService } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { OrionModule } from 'app/shared/orion/orion.module';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OrionState } from 'app/shared/orion/orion';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrionExerciseAssessmentDashboardComponent', () => {
    let comp: OrionExerciseAssessmentDashboardComponent;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let orionConnectorService: OrionConnectorService;
    let programmingAssessmentExportService: ProgrammingAssessmentRepoExportService;
    let readerStub: SinonStub;

    const programmingExercise = {
        id: 16,
        type: ExerciseType.PROGRAMMING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as ProgrammingExercise;
    const programmingSubmission = { id: 11, participation: { id: 1 } } as any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, OrionModule],
            declarations: [OrionExerciseAssessmentDashboardComponent, MockComponent(ExerciseAssessmentDashboardComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(ProgrammingSubmissionService),
                MockProvider(OrionConnectorService),
                MockProvider(ProgrammingAssessmentRepoExportService),
                MockProvider(ExerciseService),
                MockProvider(AlertService),
                MockProvider(TranslateService),
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ exerciseId: 10 }) } } },
            ],
        })
            .compileComponents()
            .then(() => {
                comp = TestBed.createComponent(OrionExerciseAssessmentDashboardComponent).componentInstance;
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
                orionConnectorService = TestBed.inject(OrionConnectorService);
                programmingAssessmentExportService = TestBed.inject(ProgrammingAssessmentRepoExportService);
                readerStub = stub(window, 'FileReader');
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
    it('downloadSubmissionInOrion with new should call send', () => {
        const sendSubmissionToOrion = spy(comp, <any>'sendSubmissionToOrion');
        const getSubmission = stub(programmingSubmissionService, 'getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment');

        getSubmission.returns(of(programmingSubmission));

        comp.downloadSubmissionInOrion('new', 0);

        expect(getSubmission).to.have.been.calledOnceWithExactly(programmingExercise.id, true, 0);
        expect(sendSubmissionToOrion).to.have.been.calledOnceWithExactly(programmingExercise.id, programmingSubmission.id, 0);
    });
    it('downloadSubmissionInOrion with number should call send', () => {
        const sendSubmissionToOrion = stub(comp, <any>'sendSubmissionToOrion');

        comp.downloadSubmissionInOrion(programmingSubmission, 0);

        expect(sendSubmissionToOrion).to.have.been.calledOnceWithExactly(programmingExercise.id, programmingSubmission.id, 0);
    });
    it('sendSubmissionToOrion should convert and call connector', () => {
        const downloadSubmissionSpy = spy(orionConnectorService, 'downloadSubmission');
        const isCloningSpy = spy(orionConnectorService, 'isCloning');
        const exportSubmissionStub = stub(programmingAssessmentExportService, 'exportReposByParticipations');
        const lockAndGetStub = stub(programmingSubmissionService, 'lockAndGetProgrammingSubmissionParticipation');

        // first it loads the submission
        lockAndGetStub.returns(of(programmingSubmission));
        // then the exported file
        const response = new HttpResponse({ body: new Blob(['Stuff', 'in blob']), status: 200 });
        exportSubmissionStub.returns(of(response));

        // mock FileReader
        const mockReader = {
            result: 'testBase64',
            // required, used to instantly trigger the callback
            // @ts-ignore
            readAsDataURL() {
                this.onloadend();
            },
        };
        readerStub.returns(mockReader);

        comp.downloadSubmissionInOrion(programmingSubmission, 0);

        expect(isCloningSpy).to.have.been.calledOnceWithExactly(true);
        expect(lockAndGetStub).to.have.been.calledOnceWith(11, 0);
        // ignore RepositoryExportOptions
        expect(exportSubmissionStub).to.have.been.calledOnceWith(16, [1]);
        expect(readerStub).to.have.been.calledOnce;
        expect(downloadSubmissionSpy).to.have.been.calledOnceWithExactly(11, 0, 'testBase64');
    });
    it('sendSubmissionToOrion should convert and report error', () => {
        const isCloningSpy = spy(orionConnectorService, 'isCloning');
        const lockAndGetStub = stub(programmingSubmissionService, 'lockAndGetProgrammingSubmissionParticipation');
        const exportSubmissionStub = stub(programmingAssessmentExportService, 'exportReposByParticipations');
        const errorSpy = spy(TestBed.inject(AlertService), 'error');

        // first it loads the submission
        lockAndGetStub.returns(of(programmingSubmission));
        // then the exported file
        const response = new HttpResponse({ body: new Blob(['Stuff', 'in blob']), status: 200 });
        exportSubmissionStub.returns(of(response));

        // mock FileReader
        const mockReader = {
            result: 'testBase64',
            // required, used to instantly trigger the callback
            // @ts-ignore
            readAsDataURL() {
                this.onerror();
            },
        };
        readerStub.returns(mockReader);

        comp.downloadSubmissionInOrion(programmingSubmission);

        expect(isCloningSpy).to.have.been.calledOnceWithExactly(true);
        expect(lockAndGetStub).to.have.been.calledOnceWith(11, 0);
        // ignore RepositoryExportOptions
        expect(exportSubmissionStub).to.have.been.calledOnceWith(16, [1]);
        expect(readerStub).to.have.been.calledOnce;
        expect(errorSpy).to.have.been.calledOnceWithExactly('artemisApp.assessmentDashboard.orion.downloadFailed');
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

        const errorSpy = spy(TestBed.inject(AlertService), 'error');
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
