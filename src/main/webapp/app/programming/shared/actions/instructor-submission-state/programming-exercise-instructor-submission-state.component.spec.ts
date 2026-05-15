import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { DebugElement } from '@angular/core';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Subject, of } from 'rxjs';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/programming/shared/actions/instructor-submission-state/programming-exercise-instructor-submission-state.component';
import { BuildRunState, ProgrammingBuildRunService } from 'app/programming/shared/services/programming-build-run.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseTriggerAllButtonComponent } from 'app/programming/shared/actions/trigger-all-button/programming-exercise-trigger-all-button.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { DurationPipe } from 'app/shared/pipes/duration.pipe';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('ProgrammingExerciseInstructorSubmissionStateComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ProgrammingExerciseInstructorSubmissionStateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorSubmissionStateComponent>;
    let debugElement: DebugElement;
    let submissionService: ProgrammingSubmissionService;
    let buildRunService: ProgrammingBuildRunService;

    let getExerciseSubmissionStateStub: ReturnType<typeof vi.spyOn>;
    let getExerciseSubmissionStateSubject: Subject<ExerciseSubmissionState>;

    let getBuildRunStateSubject: Subject<BuildRunState>;

    let triggerAllStub: ReturnType<typeof vi.spyOn>;

    const exercise = { id: 20 } as Exercise;

    const resultEtaId = '#result-eta';

    const getResultEtaContainer = () => {
        return debugElement.query(By.css(resultEtaId));
    };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [
                MockModule(NgbTooltipModule),
                ProgrammingExerciseInstructorSubmissionStateComponent,
                ProgrammingExerciseTriggerAllButtonComponent,
                ButtonComponent,
                MockDirective(FeatureToggleDirective),
                MockDirective(FeatureToggleLinkDirective),
                TranslatePipeMock,
                MockDirective(TranslateDirective),
                MockPipe(DurationPipe),
            ],
            providers: [
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructorSubmissionStateComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                submissionService = TestBed.inject(ProgrammingSubmissionService);
                buildRunService = TestBed.inject(ProgrammingBuildRunService);

                getExerciseSubmissionStateSubject = new Subject<ExerciseSubmissionState>();
                getExerciseSubmissionStateStub = vi.spyOn(submissionService, 'getSubmissionStateOfExercise').mockReturnValue(getExerciseSubmissionStateSubject);

                getBuildRunStateSubject = new Subject<BuildRunState>();
                vi.spyOn(buildRunService, 'getBuildRunUpdates').mockReturnValue(getBuildRunStateSubject);

                triggerAllStub = vi.spyOn(submissionService, 'triggerInstructorBuildForParticipationsOfExercise').mockReturnValue(of());
                vi.spyOn(submissionService, 'triggerInstructorBuildForAllParticipationsOfExercise').mockReturnValue(of());
            });
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
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

    it('should show the result eta if there is at least one building submission', async () => {
        vi.useFakeTimers();
        const isBuildingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        fixture.componentRef.setInput('exercise', exercise as ProgrammingExercise);

        fixture.detectChanges();
        getExerciseSubmissionStateSubject.next(isBuildingSubmissionState);

        await vi.advanceTimersByTimeAsync(500);

        fixture.detectChanges();

        const resultEta = getResultEtaContainer();
        expect(resultEta).not.toBeNull();
    });

    it('should not show the result eta if there is no building submission', () => {
        const isNotBuildingSubmission = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        fixture.componentRef.setInput('exercise', exercise as ProgrammingExercise);

        fixture.detectChanges();
        getExerciseSubmissionStateSubject.next(isNotBuildingSubmission);

        fixture.detectChanges();

        const resultEta = getResultEtaContainer();
        expect(resultEta).toBeNull();
    });

    it('should show & enable the trigger all button and the build state once the build summary is loaded', async () => {
        vi.useFakeTimers();
        const noPendingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        const compressedSummary = { [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 2 };
        fixture.componentRef.setInput('exercise', exercise as ProgrammingExercise);

        fixture.detectChanges();
        getExerciseSubmissionStateSubject.next(noPendingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        await vi.advanceTimersByTimeAsync(500);

        fixture.detectChanges();

        expect(getExerciseSubmissionStateStub).toHaveBeenCalledOnce();
        expect(getExerciseSubmissionStateStub).toHaveBeenCalledWith(exercise.id);

        expect(comp.hasFailedSubmissions()).toBe(false);
        expect(comp.isBuildingFailedSubmissions()).toBe(false);
        expect(comp.buildingSummary()).toEqual(compressedSummary);

        expect(getTriggerAllButton()).not.toBeNull();
        expect(getTriggerAllButton().disabled).toBe(false);
        expect(getTriggerFailedButton()).not.toBeNull();
        expect(getTriggerFailedButton().disabled).toBe(true);
        expect(getBuildState()).not.toBeNull();
    });

    it('should show & enable both buttons and the build state once the build summary is loaded when a failed submission exists', async () => {
        vi.useFakeTimers();
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
        fixture.componentRef.setInput('exercise', exercise as ProgrammingExercise);

        fixture.detectChanges();
        getExerciseSubmissionStateSubject.next(noPendingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        await vi.advanceTimersByTimeAsync(500);

        fixture.detectChanges();

        expect(getExerciseSubmissionStateStub).toHaveBeenCalledWith(exercise.id);

        expect(comp.hasFailedSubmissions()).toBe(true);
        expect(comp.isBuildingFailedSubmissions()).toBe(false);
        expect(comp.buildingSummary()).toEqual(compressedSummary);

        expect(getResultEtaContainer()).not.toBeNull();
        expect(getTriggerAllButton()).not.toBeNull();
        expect(getTriggerAllButton().disabled).toBe(false);
        expect(getTriggerFailedButton()).not.toBeNull();
        expect(getTriggerFailedButton().disabled).toBe(false);
        expect(getBuildState()).not.toBeNull();
    });

    it('should trigger the appropriate service method on trigger failed and set the isBuildingFailedSubmissionsState until the request returns a response', () => {
        const failedSubmissionParticipationIds = [333];
        const triggerInstructorBuildForParticipationsOfExerciseSubject = new Subject<void>();
        triggerAllStub.mockReturnValue(triggerInstructorBuildForParticipationsOfExerciseSubject);
        const getFailedSubmissionParticipationsForExerciseStub = vi.spyOn(submissionService, 'getSubmissionCountByType').mockReturnValue(failedSubmissionParticipationIds);
        // Component must have at least one failed submission for the button to be enabled.
        fixture.componentRef.setInput('exercise', exercise as ProgrammingExercise);
        comp.buildingSummary.set({ [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 1, [ProgrammingSubmissionState.HAS_FAILED_SUBMISSION]: 1 });
        comp.hasFailedSubmissions.set(true);

        fixture.detectChanges();

        const triggerButton = getTriggerFailedButton();
        expect(triggerButton).not.toBeNull();
        expect(triggerButton.disabled).toBe(false);

        // Button is clicked.
        triggerButton.click();

        expect(comp.isBuildingFailedSubmissions()).toBe(true);
        expect(getFailedSubmissionParticipationsForExerciseStub).toHaveBeenCalledWith(comp.exercise()!.id, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        expect(triggerAllStub).toHaveBeenCalledWith(comp.exercise()!.id, failedSubmissionParticipationIds);

        fixture.detectChanges();

        // Now the request returns a response and completes — finalize() resets the flag on completion (or error).
        triggerInstructorBuildForParticipationsOfExerciseSubject.next(undefined);
        triggerInstructorBuildForParticipationsOfExerciseSubject.complete();

        fixture.detectChanges();

        expect(comp.isBuildingFailedSubmissions()).toBe(false);
    });

    it('should disable the trigger all button while a build is running and re-enable it when it is complete', async () => {
        vi.useFakeTimers();
        const isBuildingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        fixture.componentRef.setInput('exercise', exercise as ProgrammingExercise);

        fixture.detectChanges(false);
        getExerciseSubmissionStateSubject.next(isBuildingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        await vi.advanceTimersByTimeAsync(500);

        fixture.detectChanges();

        expect(getTriggerAllButton().disabled).toBe(false);

        getBuildRunStateSubject.next(BuildRunState.RUNNING);
        fixture.detectChanges();

        expect(getTriggerAllButton().disabled).toBe(true);

        getBuildRunStateSubject.next(BuildRunState.COMPLETED);
        fixture.detectChanges();

        expect(getTriggerAllButton().disabled).toBe(false);
    });
});
