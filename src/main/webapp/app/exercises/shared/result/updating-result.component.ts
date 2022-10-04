import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { orderBy as _orderBy } from 'lodash-es';
import { Subscription } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { RepositoryService } from 'app/exercises/shared/result/repository.service';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/exercises/programming/participate/programming-submission.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Submission, SubmissionType } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { MissingResultInfo } from 'app/exercises/shared/result/result.component';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { hasParticipationChanged } from 'app/exercises/shared/participation/participation.utils';

/**
 * A component that wraps the result component, updating its result on every websocket result event for the logged-in user.
 * If the participation changes, the newest result from its result array will be used.
 * If the participation does not have any results, there will be no result displayed, until a new result is received through the websocket.
 */
@Component({
    selector: 'jhi-updating-result',
    templateUrl: './updating-result.component.html',
    providers: [ResultService, RepositoryService],
})
export class UpdatingResultComponent implements OnChanges, OnDestroy {
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;
    @Input() short = false;
    @Input() showUngradedResults: boolean;
    @Input() showBadge: boolean;
    @Input() showTestNames = false;
    /**
     * @property personalParticipation Whether the participation belongs to the user (by being a student) or not (by being an instructor)
     */
    @Input() personalParticipation = true;

    @Output() onParticipationChange = new EventEmitter<void>();

    result?: Result;
    isBuilding: boolean;
    missingResultInfo = MissingResultInfo.NONE;
    public resultSubscription: Subscription;
    public submissionSubscription: Subscription;

    constructor(private participationWebsocketService: ParticipationWebsocketService, private submissionService: ProgrammingSubmissionService) {}

    /**
     * If there are changes, reorders the participation results and subscribes for new participation results.
     * @param changes The hashtable of occurred changes represented as SimpleChanges object.
     */
    ngOnChanges(changes: SimpleChanges) {
        if (hasParticipationChanged(changes)) {
            // Sort participation results by completionDate desc.
            if (this.participation.results) {
                this.participation.results = _orderBy(this.participation.results, 'completionDate', 'desc');
            }
            // The latest result is the first rated result in the sorted array (=newest) or any result if the option is active to show ungraded results.
            const latestResult = this.participation.results && this.participation.results.find(({ rated }) => this.showUngradedResults || rated === true);
            // Make sure that the participation result is connected to the newest result.
            this.result = latestResult ? { ...latestResult, participation: this.participation } : undefined;
            this.missingResultInfo = MissingResultInfo.NONE;

            this.subscribeForNewResults();
            // Currently submissions are only used for programming exercises to visualize the build process.
            if (this.exercise && this.exercise.type === ExerciseType.PROGRAMMING) {
                this.subscribeForNewSubmissions();
            }
        }
    }

    /**
     * On component close, unsubscribe from all previous subscriptions.
     */
    ngOnDestroy() {
        if (this.resultSubscription) {
            this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(this.participation.id!, this.exercise);
            this.resultSubscription.unsubscribe();
        }
        if (this.submissionSubscription) {
            this.submissionService.unsubscribeForLatestSubmissionOfParticipation(this.participation.id!);
            this.submissionSubscription.unsubscribe();
        }
    }

    /**
     * Subscribes to new results for the current participation.
     */
    subscribeForNewResults() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id!, this.personalParticipation, this.exercise ? this.exercise.id : undefined)
            .pipe(
                // Ignore initial null result of subscription
                filter((result) => !!result),
                // Ignore ungraded results if ungraded results are supposed to be ignored.
                filter((result: Result) => this.showUngradedResults || result.rated === true),
                map((result) => ({ ...result, completionDate: result.completionDate ? dayjs(result.completionDate) : undefined, participation: this.participation })),
                tap((result) => {
                    this.result = result;
                    this.onParticipationChange.emit();
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
            .getLatestPendingSubmissionByParticipationId(this.participation.id!, this.exercise.id!, this.personalParticipation)
            .pipe(
                filter(({ submission }) => this.shouldUpdateSubmissionState(submission)),
                tap(({ submissionState }) => this.updateSubmissionState(submissionState)),
            )
            .subscribe();
    }

    private generateMissingResultInfoForFailedProgrammingExerciseSubmission() {
        // Students have more options to check their code if the offline IDE is activated, so we suggest different actions
        if ((this.exercise as ProgrammingExercise).allowOfflineIde) {
            return MissingResultInfo.FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE;
        }
        return MissingResultInfo.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE;
    }

    /**
     * Checks if a status update should be shown for this submission.
     *
     * @param submission for which a status update should be shown.
     * @private
     */
    private shouldUpdateSubmissionState(submission?: Submission): boolean {
        // The updating result must ignore submissions that are ungraded if ungraded results should not be shown
        // (otherwise the building animation will be shown even though not relevant).
        return (
            this.showUngradedResults ||
            !submission ||
            !this.exercise.dueDate ||
            submission.type === SubmissionType.INSTRUCTOR ||
            submission.type === SubmissionType.TEST ||
            dayjs(submission.submissionDate).isBefore(getExerciseDueDate(this.exercise, this.participation))
        );
    }

    /**
     * Updates the shown status based on the given state of a submission.
     *
     * @param submissionState the submission is currently in.
     * @private
     */
    private updateSubmissionState(submissionState: ProgrammingSubmissionState) {
        this.isBuilding = submissionState === ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION;

        if (submissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION) {
            this.missingResultInfo = this.generateMissingResultInfoForFailedProgrammingExerciseSubmission();
        } else {
            // everything ok, remove the warning
            this.missingResultInfo = MissingResultInfo.NONE;
        }
    }
}
