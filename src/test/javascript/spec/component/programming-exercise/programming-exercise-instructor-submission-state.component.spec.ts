import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { DebugElement } from '@angular/core';
import { of, Subject } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-submission-state.component';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { BuildRunState, ProgrammingBuildRunService } from 'app/exercises/programming/participate/programming-build-run.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseTriggerAllButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-trigger-all-button.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/button.component';
import { DurationPipe } from 'app/shared/pipes/duration.pipe';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';

describe('ProgrammingExerciseInstructorSubmissionStateComponent', () => {
    let comp: ProgrammingExerciseInstructorSubmissionStateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorSubmissionStateComponent>;
    let debugElement: DebugElement;
    let submissionService: ProgrammingSubmissionService;
    let buildRunService: ProgrammingBuildRunService;

    let getExerciseSubmissionStateStub: jest.SpyInstance;
    let getExerciseSubmissionStateSubject: Subject<ExerciseSubmissionState>;

    let getBuildRunStateSubject: Subject<BuildRunState>;

    let triggerAllStub: jest.SpyInstance;

    const exercise = { id: 20 } as Exercise;

    const resultEtaId = '#result-eta';

    const getResultEtaContainer = () => {
        return debugElement.query(By.css(resultEtaId));
    };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ProgrammingExerciseInstructorSubmissionStateComponent,
                ProgrammingExerciseTriggerAllButtonComponent,
                ButtonComponent,
                FeatureToggleDirective,
                FeatureToggleLinkDirective,
                TranslatePipeMock,
                MockDirective(NgbTooltip),
                MockDirective(TranslateDirective),
                MockPipe(DurationPipe),
            ],
            providers: [
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructorSubmissionStateComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                submissionService = debugElement.injector.get(ProgrammingSubmissionService);
                buildRunService = debugElement.injector.get(ProgrammingBuildRunService);

                getExerciseSubmissionStateSubject = new Subject<ExerciseSubmissionState>();
                getExerciseSubmissionStateStub = jest.spyOn(submissionService, 'getSubmissionStateOfExercise').mockReturnValue(getExerciseSubmissionStateSubject);

                getBuildRunStateSubject = new Subject<BuildRunState>();
                jest.spyOn(buildRunService, 'getBuildRunUpdates').mockReturnValue(getBuildRunStateSubject);

                triggerAllStub = jest.spyOn(submissionService, 'triggerInstructorBuildForParticipationsOfExercise').mockReturnValue(of());
                jest.spyOn(submissionService, 'triggerInstructorBuildForAllParticipationsOfExercise').mockReturnValue(of());
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const getTriggerAllButton = () => {
        const triggerButton = debugElement.query(By.css('#trigger-all-button button'));
        return triggerButton ? triggerButton.nativeElement : null;
    };

    const getTriggerFailedButton = () => {
        const triggerButton = debugElement.query(By.css('#trigger-failed-button button'));
        return triggerButton ? triggerButton.nativeElement : null;
    };

    const getBuildState = () => {
        const buildState = debugElement.query(By.css('#build-state'));
        return buildState ? buildState.nativeElement : null;
    };

    it('should not show the component before the build summary is retrieved', () => {
        expect(getTriggerAllButton()).toBeNull();
        expect(getTriggerFailedButton()).toBeNull();
        expect(getBuildState()).toBeNull();
    });

    it('should show the result eta if there is at least one building submission', fakeAsync(() => {
        const isBuildingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(isBuildingSubmissionState);

        tick(500);

        fixture.detectChanges();

        const resultEta = getResultEtaContainer();
        expect(resultEta).not.toBeNull();
    }));

    it('should not show the result eta if there is no building submission', () => {
        const isNotBuildingSubmission = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(isNotBuildingSubmission);

        fixture.detectChanges();

        const resultEta = getResultEtaContainer();
        expect(resultEta).toBeNull();
    });

    it('should show & enable the trigger all button and the build state once the build summary is loaded', fakeAsync(() => {
        const noPendingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        const compressedSummary = { [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 2 };
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(noPendingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        tick(500);

        fixture.detectChanges();

        expect(getExerciseSubmissionStateStub).toHaveBeenCalledOnce();
        expect(getExerciseSubmissionStateStub).toHaveBeenCalledWith(exercise.id);

        expect(comp.hasFailedSubmissions).toBeFalse();
        expect(comp.isBuildingFailedSubmissions).toBeFalse();
        expect(comp.buildingSummary).toEqual(compressedSummary);

        expect(getTriggerAllButton()).not.toBeNull();
        expect(getTriggerAllButton().disabled).toBeFalse();
        expect(getTriggerFailedButton()).not.toBeNull();
        expect(getTriggerFailedButton().disabled).toBeTrue();
        expect(getBuildState()).not.toBeNull();
    }));

    it('should show & enable both buttons and the build state once the build summary is loaded when a failed submission exists', fakeAsync(() => {
        const noPendingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 55 },
            4: { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 76 },
            5: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: undefined, participationId: 76 },
        } as ExerciseSubmissionState;
        const compressedSummary = {
            [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 1,
            [ProgrammingSubmissionState.HAS_FAILED_SUBMISSION]: 1,
            [ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION]: 1,
        };
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(noPendingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        tick(500);

        fixture.detectChanges();

        expect(getExerciseSubmissionStateStub).toHaveBeenCalledWith(exercise.id);

        expect(comp.hasFailedSubmissions).toBeTrue();
        expect(comp.isBuildingFailedSubmissions).toBeFalse();
        expect(comp.buildingSummary).toEqual(compressedSummary);

        expect(getResultEtaContainer()).not.toBeNull();
        expect(getTriggerAllButton()).not.toBeNull();
        expect(getTriggerAllButton().disabled).toBeFalse();
        expect(getTriggerFailedButton()).not.toBeNull();
        expect(getTriggerFailedButton().disabled).toBeFalse();
        expect(getBuildState()).not.toBeNull();
    }));

    it('should trigger the appropriate service method on trigger failed and set the isBuildingFailedSubmissionsState until the request returns a response', () => {
        const failedSubmissionParticipationIds = [333];
        const triggerInstructorBuildForParticipationsOfExerciseSubject = new Subject<void>();
        triggerAllStub.mockReturnValue(triggerInstructorBuildForParticipationsOfExerciseSubject);
        const getFailedSubmissionParticipationsForExerciseStub = jest.spyOn(submissionService, 'getSubmissionCountByType').mockReturnValue(failedSubmissionParticipationIds);
        // Component must have at least one failed submission for the button to be enabled.
        comp.exercise = exercise as ProgrammingExercise;
        comp.buildingSummary = { [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 1, [ProgrammingSubmissionState.HAS_FAILED_SUBMISSION]: 1 };
        comp.hasFailedSubmissions = true;

        fixture.detectChanges();

        const triggerButton = getTriggerFailedButton();
        expect(triggerButton).not.toBeNull();
        expect(triggerButton.disabled).toBeFalse();

        // Button is clicked.
        triggerButton.click();

        expect(comp.isBuildingFailedSubmissions).toBeTrue();
        expect(getFailedSubmissionParticipationsForExerciseStub).toHaveBeenCalledWith(comp.exercise.id, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        expect(triggerAllStub).toHaveBeenCalledWith(comp.exercise.id, failedSubmissionParticipationIds);

        fixture.detectChanges();

        // Now the request returns a response.
        triggerInstructorBuildForParticipationsOfExerciseSubject.next(undefined);

        fixture.detectChanges();

        expect(comp.isBuildingFailedSubmissions).toBeFalse();
    });

    it('should disable the trigger all button while a build is running and re-enable it when it is complete', fakeAsync(() => {
        const isBuildingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(isBuildingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        tick(500);

        fixture.detectChanges();

        expect(getTriggerAllButton().disabled).toBeFalse();

        getBuildRunStateSubject.next(BuildRunState.RUNNING);
        fixture.detectChanges();

        expect(getTriggerAllButton().disabled).toBeTrue();

        getBuildRunStateSubject.next(BuildRunState.COMPLETED);
        fixture.detectChanges();

        expect(getTriggerAllButton().disabled).toBeFalse();
    }));
});
