import { Component, OnDestroy, OnInit, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Subscription } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { RepositoryService } from 'app/programming/shared/services/repository.service';
import dayjs from 'dayjs/esm';
import { BuildTimingInfo, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ResultService } from 'app/exercise/result/result.service';
import { Submission, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import { getLatestResultOfStudentParticipation } from 'app/exercise/participation/participation.utils';
import { MissingResultInformation, isAIResultAndIsBeingProcessed, isAthenaAIResult } from 'app/exercise/result/result.utils';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { ResultComponent } from '../result.component';

/**
 * A component that wraps the result component, updating its result on every websocket result event for the logged-in user.
 * If the participation changes, the newest result from its result array will be used.
 * If the participation does not have any results, there will be no result displayed, until a new result is received through the websocket.
 */
@Component({
    selector: 'jhi-updating-result',
    templateUrl: './updating-result.component.html',
    providers: [ResultService, RepositoryService],
    imports: [ResultComponent],
})
export class UpdatingResultComponent implements OnInit, OnDestroy {
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private submissionService = inject(ProgrammingSubmissionService);
    private profileService = inject(ProfileService);

    readonly exercise = input<Exercise>(undefined!);
    readonly participation = input<StudentParticipation>(undefined!);
    readonly short = input(true);
    readonly showUngradedResults = input(false);
    readonly showBadge = input(false);
    readonly showIcon = input(true);
    readonly isInSidebarCard = input(false);
    readonly showCompletion = input(true);
    readonly showProgressBar = input(false);
    readonly showProgressBarBorder = input(false);
    readonly showResult = output<void>();
    /**
     * @property personalParticipation Whether the participation belongs to the user (by being a student) or not (by being an instructor)
     */
    readonly personalParticipation = input(true);

    readonly onParticipationChange = output<void>();

    readonly result = signal<Result | undefined>(undefined);
    readonly isBuilding = signal(false);
    readonly isQueued = signal(false);
    readonly estimatedCompletionDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly buildStartDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly showProgressBarInResult = signal(false);
    readonly missingResultInfo = signal(MissingResultInformation.NONE);
    public resultSubscription?: Subscription;
    public submissionSubscription?: Subscription;

    isLocalCIEnabled = true;

    /**
     * Re-subscription trigger: derives the participation *id* so the effect below only re-runs when the id actually
     * changes (a `computed` notifies on value change, `Object.is`), ignoring same-id object replacements. This replaces
     * the former `hasParticipationChanged(changes)` guard, which read `SimpleChanges.previousValue.id`.
     */
    private readonly participationId = computed(() => this.participation()?.id);

    constructor() {
        // Replaces ngOnChanges: when the participation id changes, pick the latest result and (re)open the websocket
        // subscriptions. The effect runs after ngOnInit on the first change detection, so isLocalCIEnabled (set in
        // ngOnInit) is already settled. The body runs untracked so reads of exercise()/showUngradedResults()/etc. and
        // the signal writes are not themselves triggers — only participationId() is.
        effect(() => {
            this.participationId();
            untracked(() => {
                const participation = this.participation();
                if (!participation) {
                    // No participation to display: tear down any active websocket subscriptions so stale updates can no
                    // longer mutate state, and so ngOnDestroy does not later dereference an undefined participation.
                    this.resultSubscription?.unsubscribe();
                    this.resultSubscription = undefined;
                    this.submissionSubscription?.unsubscribe();
                    this.submissionSubscription = undefined;
                    return;
                }
                this.result.set(getLatestResultOfStudentParticipation(participation, this.showUngradedResults(), true));
                this.missingResultInfo.set(MissingResultInformation.NONE);

                this.subscribeForNewResults();
                // Currently submissions are only used for programming exercises to visualize the build process.
                if (this.exercise()?.type === ExerciseType.PROGRAMMING) {
                    this.subscribeForNewSubmissions();
                }

                if (this.isLocalCIEnabled) {
                    this.showProgressBarInResult.set(this.showProgressBar());
                }

                if (this.result()) {
                    this.showResult.emit();
                }
            });
        });
    }

    ngOnInit() {
        this.isLocalCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    }

    /**
     * On component close, unsubscribe from all previous subscriptions.
     */
    ngOnDestroy() {
        const participation = this.participation();
        if (this.resultSubscription) {
            this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(participation.id!, this.exercise());
            this.resultSubscription.unsubscribe();
        }
        if (this.submissionSubscription) {
            // Release this component's subscription first, so the service only sees remaining observers
            // (e.g. the exercise header and the code editor show the same participation simultaneously)
            // when deciding whether the shared websocket state may be torn down.
            this.submissionSubscription.unsubscribe();
            this.submissionService.unsubscribeForLatestSubmissionOfParticipation(participation.id!);
        }
    }

    /**
     * Subscribes to new results for the current participation.
     */
    subscribeForNewResults() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        const exercise = this.exercise();
        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation().id!, this.personalParticipation(), exercise ? exercise.id : undefined)
            .pipe(
                // Ignore initial null result of subscription
                filter((result) => !!result),
                // Ignore ungraded results if ungraded results are supposed to be ignored.
                // If the result is a preliminary feedback(being generated), show it
                filter((result: Result) => this.showUngradedResults() || result.rated === true || isAthenaAIResult(result)),
                map((result): Result => ({ ...result, completionDate: convertDateFromServer(result.completionDate) }) satisfies Result),
                tap((result) => {
                    const showUngradedResults = this.showUngradedResults();
                    if ((isAthenaAIResult(result) && isAIResultAndIsBeingProcessed(result)) || result.rated) {
                        this.result.set(result);
                    } else if (result.rated === false && showUngradedResults) {
                        this.result.set(result);
                    } else {
                        this.result.set(getLatestResultOfStudentParticipation(this.participation(), showUngradedResults, false));
                    }
                    this.onParticipationChange.emit();
                    if (result) {
                        this.showResult.emit();
                    }
                }),
            )
            .subscribe();
    }

    /**
     * Subscribe for incoming submissions that indicate that the build process has started in the CI.
     * Will emit a null value when no build is running / the current build has stopped running.
     */
    subscribeForNewSubmissions() {
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        this.submissionSubscription = this.submissionService
            .getLatestPendingSubmissionByParticipationId(this.participation().id!, this.exercise().id!, this.personalParticipation())
            .pipe(
                filter(({ submission }) => this.shouldUpdateSubmissionState(submission)),
                tap(({ submissionState, buildTimingInfo, submission }) => this.updateSubmissionState(submissionState, buildTimingInfo, submission?.submissionDate)),
            )
            .subscribe();
    }

    private generateMissingResultInfoForFailedProgrammingExerciseSubmission() {
        // Students have more options to check their code if the offline IDE is activated, so we suggest different actions
        if ((this.exercise() as ProgrammingExercise).allowOfflineIde) {
            return MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE;
        }
        return MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE;
    }

    /**
     * Checks if a status update should be shown for this submission.
     *
     * @param submission for which a status update should be shown.
     */
    private shouldUpdateSubmissionState(submission?: Submission): boolean {
        // The updating result must ignore submissions that are ungraded if ungraded results should not be shown
        // (otherwise the building animation will be shown even though not relevant).
        const exercise = this.exercise();
        return (
            this.showUngradedResults() ||
            !submission ||
            !exercise.dueDate ||
            submission.type === SubmissionType.INSTRUCTOR ||
            submission.type === SubmissionType.TEST ||
            dayjs(submission.submissionDate).isBefore(getExerciseDueDate(exercise, this.participation()))
        );
    }

    /**
     * Updates the shown status based on the given state of a submission.
     *
     * @param submissionState the submission is currently in.
     * @param buildTimingInfo object container the build start time and the estimated completion time.
     * @param submissionDate the date when the submission was created.
     */
    private updateSubmissionState(submissionState: ProgrammingSubmissionState, buildTimingInfo?: BuildTimingInfo, submissionDate?: dayjs.Dayjs) {
        this.isQueued.set(submissionState === ProgrammingSubmissionState.IS_QUEUED);
        this.isBuilding.set(submissionState === ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);

        if (this.isLocalCIEnabled) {
            this.updateBuildTimingInfo(submissionState, buildTimingInfo, submissionDate);
        }

        if (submissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION) {
            this.missingResultInfo.set(this.generateMissingResultInfoForFailedProgrammingExerciseSubmission());
        } else {
            // everything ok, remove the warning
            this.missingResultInfo.set(MissingResultInformation.NONE);
        }
    }

    /**
     * Updates the build timing information based on the submission state.
     *
     * @param  submissionState - The current state of the submission.
     * @param  [buildTimingInfo] - Optional object containing the build start time and the estimated completion time.
     * @param  [submissionDate] - Optional date when the submission was created.
     */
    private updateBuildTimingInfo(submissionState: ProgrammingSubmissionState, buildTimingInfo?: BuildTimingInfo, submissionDate?: dayjs.Dayjs) {
        if (submissionState === ProgrammingSubmissionState.IS_QUEUED) {
            this.submissionService.fetchQueueReleaseDateEstimationByParticipationId(this.participation().id!).subscribe((releaseDate) => {
                if (releaseDate && !this.isBuilding()) {
                    this.estimatedCompletionDate.set(releaseDate);
                    this.buildStartDate.set(submissionDate);
                }
            });
        } else if (
            submissionState === ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION &&
            buildTimingInfo &&
            dayjs(buildTimingInfo?.estimatedCompletionDate).isAfter(dayjs())
        ) {
            this.estimatedCompletionDate.set(buildTimingInfo?.estimatedCompletionDate);
            this.buildStartDate.set(buildTimingInfo?.buildStartDate);
        } else {
            this.estimatedCompletionDate.set(undefined);
            this.buildStartDate.set(undefined);
        }
    }
}
