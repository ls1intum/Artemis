import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { RepositoryService } from 'app/exercises/shared/result/repository.service';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/exercises/programming/participate/programming-submission.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Submission, SubmissionType } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { getLatestResultOfStudentParticipation, hasParticipationChanged } from 'app/exercises/shared/participation/participation.utils';
import { MissingResultInformation } from 'app/exercises/shared/result/result.utils';
import { convertDateFromServer } from 'app/utils/date.utils';

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
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private submissionService = inject(ProgrammingSubmissionService);

    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;
    @Input() short = true;
    @Input() showUngradedResults = false;
    @Input() showBadge = false;
    @Input() showIcon = true;
    @Input() isInSidebarCard = false;
    @Output() showResult = new EventEmitter<void>();
    /**
     * @property personalParticipation Whether the participation belongs to the user (by being a student) or not (by being an instructor)
     */
    @Input() personalParticipation = true;

    @Output() onParticipationChange = new EventEmitter<void>();

    result?: Result;
    isBuilding: boolean;
    missingResultInfo = MissingResultInformation.NONE;
    public resultSubscription: Subscription;
    public submissionSubscription: Subscription;

    /**
     * If there are changes, reorders the participation results and subscribes for new participation results.
     * @param changes The hashtable of occurred changes represented as SimpleChanges object.
     */
    ngOnChanges(changes: SimpleChanges) {
        if (hasParticipationChanged(changes)) {
            this.result = getLatestResultOfStudentParticipation(this.participation, this.showUngradedResults);
            this.missingResultInfo = MissingResultInformation.NONE;

            this.subscribeForNewResults();
            // Currently submissions are only used for programming exercises to visualize the build process.
            if (this.exercise?.type === ExerciseType.PROGRAMMING) {
                this.subscribeForNewSubmissions();
            }

            if (this.result) {
                this.showResult.emit();
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
                map((result) => ({ ...result, completionDate: convertDateFromServer(result.completionDate), participation: this.participation })),
                tap((result) => {
                    this.result = result;
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
     */
    private updateSubmissionState(submissionState: ProgrammingSubmissionState) {
        this.isBuilding = submissionState === ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION;

        if (submissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION) {
            this.missingResultInfo = this.generateMissingResultInfoForFailedProgrammingExerciseSubmission();
        } else {
            // everything ok, remove the warning
            this.missingResultInfo = MissingResultInformation.NONE;
        }
    }
}
