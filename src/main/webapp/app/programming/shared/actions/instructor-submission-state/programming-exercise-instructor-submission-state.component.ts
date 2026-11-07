import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { EMPTY, Subscription, merge, timer } from 'rxjs';
import { catchError, debounceTime, filter, map, switchMap, take, tap } from 'rxjs/operators';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { faChevronDown, faCircleNotch, faClock, faExclamationTriangle, faRedo } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExerciseTriggerAllButtonComponent } from '../trigger-all-button/programming-exercise-trigger-all-button.component';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DurationPipe } from 'app/foundation/pipes/duration.pipe';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { ProgrammingBuildRunService } from 'app/programming/shared/services/programming-build-run.service';

/**
 * How often the server is asked whether the run that the retry button waits for has ended, in case the message that announces it was missed.
 */
const AWAITED_RUN_STATUS_POLL_INTERVAL_MS = 30_000;

/**
 * This components provides two buttons to the instructor to interact with the students' submissions:
 * - Trigger builds for all student participations
 * - Trigger builds for failed student participations
 *
 * Also shows an info section next to the buttons about the number of building and failed submissions.
 */
@Component({
    selector: 'jhi-programming-exercise-instructor-submission-state',
    templateUrl: './programming-exercise-instructor-submission-state.component.html',
    imports: [
        FaIconComponent,
        ProgrammingExerciseTriggerAllButtonComponent,
        TumAetUiButtonDirective,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        FeatureToggleDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        DurationPipe,
    ],
})
export class ProgrammingExerciseInstructorSubmissionStateComponent implements OnInit {
    private programmingSubmissionService = inject(ProgrammingSubmissionService);
    private programmingBuildRunService = inject(ProgrammingBuildRunService);
    private websocketService = inject(WebsocketService);
    private destroyRef = inject(DestroyRef);

    FeatureToggle = FeatureToggle;
    ButtonSize = ButtonSize;
    ProgrammingSubmissionState = ProgrammingSubmissionState;

    readonly exercise = input.required<ProgrammingExercise>();
    readonly shouldToggle = input(false);
    readonly toggleBreakpoint = input<'md' | 'xl'>('xl');

    // Replaces the former constructor effect that subscribed to the submission-state stream and imperatively set three
    // signals. The building summary is now a toSignal of the exercise-id-keyed stream (switchMap re-subscribes when the
    // exercise changes and cancels the previous stream; toSignal tears it down on destroy), and the two boolean flags
    // are derived from it.
    readonly buildingSummary = toSignal(
        toObservable(computed(() => this.exercise().id)).pipe(
            filter((exerciseId): exerciseId is number => exerciseId !== undefined),
            switchMap((exerciseId) =>
                this.programmingSubmissionService.getSubmissionStateOfExercise(exerciseId).pipe(
                    map((buildState) => this.sumSubmissionStates(buildState)),
                    // If we would update the UI with every small change, it would seem very hectic. So we always take the latest value after 500ms.
                    debounceTime(500),
                ),
            ),
        ),
        { initialValue: undefined },
    );
    readonly hasFailedSubmissions = computed(() => (this.buildingSummary()?.[ProgrammingSubmissionState.HAS_FAILED_SUBMISSION] ?? 0) > 0);
    readonly hasBuildingSubmissions = computed(() => (this.buildingSummary()?.[ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION] ?? 0) > 0);
    // True from the click on the retry button until the server reports that its run has queued the last build, see triggerBuildOfFailedSubmissions.
    readonly isBuildingFailedSubmissions = signal(false);

    // The run that the retry button waits for, once the server named it. The end of a run can be announced before the response that names it arrives, so the runs that ended
    // since the click are remembered as well.
    private readonly awaitedRunId = signal<string | undefined>(undefined);
    private readonly endedRunIds = new Set<string>();
    // The request that starts the run, kept so that the state reset for another exercise can cancel it. Its response must not name a run of the previous exercise in the new state.
    private retryRequest?: Subscription;

    readonly resultEtaInMs = signal<number | undefined>(undefined);

    // Icons
    readonly faChevronDown = faChevronDown;
    faClock = faClock;
    readonly faExclamationTriangle = faExclamationTriangle;
    faCircleNotch = faCircleNotch;
    faRedo = faRedo;

    constructor() {
        // The server queues the builds in the background and announces the end of each run by name. The stream follows the exercise like buildingSummary does, because the
        // component is not recreated when the exercise input changes.
        toObservable(computed(() => this.exercise().id))
            .pipe(
                tap(() => this.resetRetryState()),
                filter((exerciseId): exerciseId is number => exerciseId !== undefined),
                switchMap((exerciseId) => this.programmingBuildRunService.getSelectedBuildRunCompletions(exerciseId)),
                takeUntilDestroyed(),
            )
            .subscribe((runId) => this.onSelectedBuildRunCompleted(runId));

        // The message that announces the end of the run is lost if the websocket connection is down at that moment, and a reconnect does not replay it. While a run is awaited,
        // the server is therefore asked for its status whenever the connection is up again, and from time to time as the connection may also have dropped for less than the
        // connection state reports.
        toObservable(this.awaitedRunId)
            .pipe(
                switchMap((runId) =>
                    runId === undefined
                        ? EMPTY
                        : merge(
                              timer(AWAITED_RUN_STATUS_POLL_INTERVAL_MS, AWAITED_RUN_STATUS_POLL_INTERVAL_MS),
                              this.websocketService.connectionState.pipe(filter((connectionState) => connectionState.connected)),
                          ).pipe(
                              switchMap(() =>
                                  this.programmingSubmissionService.getSelectedBuildRunStatus(this.exercise().id!, runId).pipe(
                                      // A status that cannot be read now is asked for again at the next tick.
                                      catchError(() => EMPTY),
                                  ),
                              ),
                              filter((status) => !status.running),
                              take(1),
                              map(() => runId),
                          ),
                ),
                takeUntilDestroyed(),
            )
            .subscribe((runId) => this.onSelectedBuildRunCompleted(runId));
    }

    ngOnInit(): void {
        this.programmingSubmissionService
            .getResultEtaInMs()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((resultEta) => this.resultEtaInMs.set(resultEta));
    }

    /**
     * Retrieve the participation ids that have a failed submission and retry their build.
     * <p>
     * The request returns at once, because the server queues the builds in the background and may have to wait for room in the build queue.
     * The button therefore stays busy until the server reports that this run is done, not until the request completes. Runs of other instructors do not count.
     */
    triggerBuildOfFailedSubmissions() {
        this.retryRequest?.unsubscribe();
        this.isBuildingFailedSubmissions.set(true);
        this.awaitedRunId.set(undefined);
        this.endedRunIds.clear();
        const failedSubmissionParticipations = this.programmingSubmissionService.getSubmissionCountByType(this.exercise().id!, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        this.retryRequest = this.programmingSubmissionService
            .triggerInstructorBuildForParticipationsOfExercise(this.exercise().id!, failedSubmissionParticipations)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: ({ runId }) => {
                    if (this.endedRunIds.has(runId)) {
                        this.resetRetryState();
                    } else {
                        this.awaitedRunId.set(runId);
                    }
                },
                error: () => this.resetRetryState(),
            });
    }

    private onSelectedBuildRunCompleted(runId: string) {
        this.endedRunIds.add(runId);
        if (runId === this.awaitedRunId()) {
            this.resetRetryState();
        }
    }

    private resetRetryState() {
        this.retryRequest?.unsubscribe();
        this.retryRequest = undefined;
        this.awaitedRunId.set(undefined);
        this.endedRunIds.clear();
        this.isBuildingFailedSubmissions.set(false);
    }

    private sumSubmissionStates = (buildState: ExerciseSubmissionState) =>
        Object.values(buildState).reduce((acc: { [state: string]: number }, { submissionState }) => {
            return cloneWith(acc, { [submissionState]: (acc[submissionState] || 0) + 1 });
        }, {});
}
