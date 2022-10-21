import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';
import { OrionExerciseAssessmentDashboardComponent } from 'app/orion/assessment/orion-exercise-assessment-dashboard.component';
import { OrionState } from 'app/shared/orion/orion';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';

describe('OrionExerciseAssessmentDashboardComponent', () => {
    let comp: OrionExerciseAssessmentDashboardComponent;
    let orionConnectorService: OrionConnectorService;
    let orionAssessmentService: OrionAssessmentService;
    let exerciseService: ExerciseService;
    let alertService: AlertService;

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
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ exerciseId: 10 }) } } },
            ],
        })
            .compileComponents()
            .then(() => {
                comp = TestBed.createComponent(OrionExerciseAssessmentDashboardComponent).componentInstance;
                orionConnectorService = TestBed.inject(OrionConnectorService);
                orionAssessmentService = TestBed.inject(OrionAssessmentService);
                exerciseService = TestBed.inject(ExerciseService);
                alertService = TestBed.inject(AlertService);
                comp.exerciseId = programmingExercise.id!;
                comp.exercise = programmingExercise;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('openAssessmentInOrion should call connector', () => {
        const assessExerciseSpy = jest.spyOn(orionConnectorService, 'assessExercise');

        comp.openAssessmentInOrion();

        expect(assessExerciseSpy).toHaveBeenCalledOnce();
        expect(assessExerciseSpy).toHaveBeenCalledWith(programmingExercise);
    });

    it('downloadSubmissionInOrion should call service', () => {
        const downloadSubmissionSpy = jest.spyOn(orionAssessmentService, 'downloadSubmissionInOrion');

        comp.downloadSubmissionInOrion(programmingSubmission, 2);

        expect(downloadSubmissionSpy).toHaveBeenCalledOnce();
        expect(downloadSubmissionSpy).toHaveBeenCalledWith(comp.exerciseId, programmingSubmission, 2, false);
    });

    it('ngOnInit should subscribe correctly', fakeAsync(() => {
        const orionState = { opened: 40, building: false, cloning: false } as OrionState;
        const stateObservable = new BehaviorSubject(orionState);
        const orionStateStub = jest.spyOn(orionConnectorService, 'state').mockReturnValue(stateObservable);

        const response = of(new HttpResponse({ body: programmingExercise, status: 200 }));
        const getForTutorsStub = jest.spyOn(exerciseService, 'getForTutors').mockReturnValue(response);

        comp.ngOnInit();
        tick();

        expect(comp.exerciseId).toBe(10);
        expect(comp.exercise).toEqual(programmingExercise);
        expect(comp.orionState).toEqual(orionState);

        expect(getForTutorsStub).toHaveBeenCalledOnce();
        expect(getForTutorsStub).toHaveBeenCalledWith(10);
        expect(orionStateStub).toHaveBeenCalledOnce();
        expect(orionStateStub).toHaveBeenCalledWith();
    }));

    it('ngOnInit should deal with error correctly', fakeAsync(() => {
        const orionState = { opened: 40, building: false, cloning: false } as OrionState;
        const stateObservable = new BehaviorSubject(orionState);
        const orionStateStub = jest.spyOn(orionConnectorService, 'state').mockReturnValue(stateObservable);

        const error = new HttpErrorResponse({ status: 400 });
        const getForTutorsStub = jest.spyOn(exerciseService, 'getForTutors').mockReturnValue(throwError(() => error));

        const errorSpy = jest.spyOn(alertService, 'error');
        // counter the initialization in beforeEach. as any required to cheat the typecheck
        comp.exercise = undefined as any;

        comp.ngOnInit();
        tick();

        expect(comp.exerciseId).toBe(10);
        expect(comp.exercise).toBeUndefined();
        expect(comp.orionState).toEqual(orionState);

        expect(getForTutorsStub).toHaveBeenCalledOnce();
        expect(getForTutorsStub).toHaveBeenCalledWith(10);
        expect(orionStateStub).toHaveBeenCalledOnce();
        expect(orionStateStub).toHaveBeenCalledWith();
        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('error.http.400');
    }));
});
