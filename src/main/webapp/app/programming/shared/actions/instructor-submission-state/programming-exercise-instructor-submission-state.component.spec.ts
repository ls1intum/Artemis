import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { DebugElement } from '@angular/core';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Subject, of } from 'rxjs';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/programming/shared/actions/instructor-submission-state/programming-exercise-instructor-submission-state.component';
import { BuildRunState, ProgrammingBuildRunService, SelectedBuildRun } from 'app/programming/shared/services/programming-build-run.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseTriggerAllButtonComponent } from 'app/programming/shared/actions/trigger-all-button/programming-exercise-trigger-all-button.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { DurationPipe } from 'app/foundation/pipes/duration.pipe';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('ProgrammingExerciseInstructorSubmissionStateComponent', () => {
    let comp: ProgrammingExerciseInstructorSubmissionStateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorSubmissionStateComponent>;
    let debugElement: DebugElement;
    let submissionService: ProgrammingSubmissionService;
    let buildRunService: ProgrammingBuildRunService;

    let getExerciseSubmissionStateStub: ReturnType<typeof vi.spyOn>;
    let getExerciseSubmissionStateSubject: Subject<ExerciseSubmissionState>;

    let getBuildRunStateSubject: Subject<BuildRunState>;
    let selectedBuildRunCompletionsSubject: Subject<string>;
    let getSelectedBuildRunCompletionsStub: ReturnType<typeof vi.spyOn>;

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

                selectedBuildRunCompletionsSubject = new Subject<string>();
                getSelectedBuildRunCompletionsStub = vi.spyOn(buildRunService, 'getSelectedBuildRunCompletions').mockReturnValue(selectedBuildRunCompletionsSubject);

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

        // Wait for the 500ms debounce.
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

        // Wait for the 500ms debounce.
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

    describe('retry of the failed builds', () => {
        const failedSubmissionParticipationIds = [333];
        let triggerRequestSubject: Subject<SelectedBuildRun>;

        const showFailedSubmission = async () => {
            vi.useFakeTimers();
            triggerRequestSubject = new Subject<SelectedBuildRun>();
            triggerAllStub.mockReturnValue(triggerRequestSubject);
            vi.spyOn(submissionService, 'getSubmissionCountByType').mockReturnValue(failedSubmissionParticipationIds);
            // Component must have at least one failed submission for the button to be enabled. buildingSummary/
            // hasFailedSubmissions are derived from the submission-state stream now, so drive them through it.
            const submissionStateWithFailure = {
                1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 55 },
                4: { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 333 },
            } as ExerciseSubmissionState;
            fixture.componentRef.setInput('exercise', exercise as ProgrammingExercise);

            fixture.detectChanges();
            getExerciseSubmissionStateSubject.next(submissionStateWithFailure);
            // Wait for the 500ms debounce.
            await vi.advanceTimersByTimeAsync(500);
            fixture.detectChanges();
        };

        it('should call the service on click and keep the button busy after the request returned until the server reports its own run as completed', async () => {
            await showFailedSubmission();
            const triggerButton = getTriggerFailedButton();
            expect(triggerButton).not.toBeNull();
            expect(triggerButton.disabled).toBe(false);

            triggerButton.click();

            expect(comp.isBuildingFailedSubmissions()).toBe(true);
            expect(triggerAllStub).toHaveBeenCalledWith(comp.exercise()!.id, failedSubmissionParticipationIds);

            // The server answers at once and queues the builds in the background, possibly after waiting for room in the build queue.
            triggerRequestSubject.next({ runId: 'run-1' });
            triggerRequestSubject.complete();
            fixture.detectChanges();

            expect(comp.isBuildingFailedSubmissions()).toBe(true);
            expect(getTriggerFailedButton().disabled).toBe(true);

            selectedBuildRunCompletionsSubject.next('run-1');
            fixture.detectChanges();

            expect(comp.isBuildingFailedSubmissions()).toBe(false);
            expect(getTriggerFailedButton().disabled).toBe(false);
        });

        it('should not take the end of a run of another instructor for its own', async () => {
            await showFailedSubmission();

            getTriggerFailedButton().click();
            triggerRequestSubject.next({ runId: 'run-1' });
            triggerRequestSubject.complete();
            selectedBuildRunCompletionsSubject.next('run-2');
            fixture.detectChanges();

            expect(comp.isBuildingFailedSubmissions()).toBe(true);

            selectedBuildRunCompletionsSubject.next('run-1');
            expect(comp.isBuildingFailedSubmissions()).toBe(false);
        });

        it('should become available again when the run ended before the response that names it arrived', async () => {
            await showFailedSubmission();

            getTriggerFailedButton().click();
            selectedBuildRunCompletionsSubject.next('run-1');
            expect(comp.isBuildingFailedSubmissions()).toBe(true);

            triggerRequestSubject.next({ runId: 'run-1' });
            triggerRequestSubject.complete();

            expect(comp.isBuildingFailedSubmissions()).toBe(false);
        });

        it('should stay busy when the request returns before the server reported the end of the run', async () => {
            await showFailedSubmission();

            getTriggerFailedButton().click();
            triggerRequestSubject.next({ runId: 'run-1' });
            triggerRequestSubject.complete();
            fixture.detectChanges();

            expect(comp.isBuildingFailedSubmissions()).toBe(true);
        });

        it('should make the button available again when the request fails', async () => {
            await showFailedSubmission();

            getTriggerFailedButton().click();
            expect(comp.isBuildingFailedSubmissions()).toBe(true);

            triggerRequestSubject.error(new Error('request failed'));
            fixture.detectChanges();

            expect(comp.isBuildingFailedSubmissions()).toBe(false);
        });

        it('should follow the exercise when it changes and drop the run of the previous exercise', async () => {
            await showFailedSubmission();
            expect(getSelectedBuildRunCompletionsStub).toHaveBeenCalledOnce();
            expect(getSelectedBuildRunCompletionsStub).toHaveBeenLastCalledWith(20);

            getTriggerFailedButton().click();
            triggerRequestSubject.next({ runId: 'run-1' });
            expect(comp.isBuildingFailedSubmissions()).toBe(true);

            fixture.componentRef.setInput('exercise', { id: 21 } as ProgrammingExercise);
            fixture.detectChanges();
            await vi.advanceTimersByTimeAsync(0);

            expect(getSelectedBuildRunCompletionsStub).toHaveBeenCalledTimes(2);
            expect(getSelectedBuildRunCompletionsStub).toHaveBeenLastCalledWith(21);
            expect(comp.isBuildingFailedSubmissions()).toBe(false);
        });
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

        // Wait for the 500ms debounce.
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
