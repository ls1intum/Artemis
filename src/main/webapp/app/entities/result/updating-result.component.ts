import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { orderBy as _orderBy } from 'lodash';
import { Subscription } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';
import { hasParticipationChanged, StudentParticipation } from 'app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { Result, ResultService } from '.';
import { RepositoryService } from 'app/entities/repository/repository.service';

import * as moment from 'moment';
import { Exercise, ExerciseType } from 'app/entities/exercise';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { SubmissionType } from 'app/entities/submission';

/**
 * A component that wraps the result component, updating its result on every websocket result event for the logged in user.
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
    @Input() showGradedBadge: boolean;
    @Input() showTestNames = false;

    result: Result | null;
    isBuilding: boolean;
    public resultSubscription: Subscription;
    public submissionSubscription: Subscription;

    constructor(private participationWebsocketService: ParticipationWebsocketService, private submissionService: ProgrammingSubmissionService) {}

    ngOnChanges(changes: SimpleChanges) {
        if (hasParticipationChanged(changes)) {
            // Sort participation results by completionDate desc.
            if (this.participation.results) {
                this.participation.results = _orderBy(this.participation.results, 'completionDate', 'desc');
            }
            // The latest result is the first rated result in the sorted array (=newest) or any result if the option is active to show ungraded results.
            const latestResult = this.participation.results && this.participation.results.find(({ rated }) => this.showUngradedResults || rated === true);
            // Make sure that the participation result is connected to the newest result.
            this.result = latestResult ? { ...latestResult, participation: this.participation } : null;

            this.subscribeForNewResults();
            // Currently submissions are only used for programming exercises to visualize the build process.
            if (this.exercise && this.exercise.type === ExerciseType.PROGRAMMING) {
                this.subscribeForNewSubmissions();
            }
        }
    }

    ngOnDestroy() {
        if (this.resultSubscription) {
            this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(this.participation.id, this.exercise);
            this.resultSubscription.unsubscribe();
        }
        if (this.submissionSubscription) {
            this.submissionService.unsubscribeForLatestSubmissionOfParticipation(this.participation.id);
            this.submissionSubscription.unsubscribe();
        }
    }

    subscribeForNewResults() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id)
            .pipe(
                // Ignore initial null result of subscription
                filter(result => !!result),
                // Ignore ungraded results if ungraded results are supposed to be ignored.
                filter((result: Result) => this.showUngradedResults || result.rated),
                map(result => ({ ...result, completionDate: result.completionDate != null ? moment(result.completionDate) : null, participation: this.participation })),
                tap(result => (this.result = result)),
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
            .getLatestPendingSubmissionByParticipationId(this.participation.id, this.exercise.id)
            .pipe(
                // The updating result must ignore submissions that are ungraded if ungraded results should not be shown (otherwise the building animation will be shown even though not relevant).
                filter(
                    ({ submission }) =>
                        this.showUngradedResults ||
                        !submission ||
                        !this.exercise.dueDate ||
                        submission.type === SubmissionType.INSTRUCTOR ||
                        submission.type === SubmissionType.TEST ||
                        this.exercise.dueDate.isAfter(moment(submission.submissionDate!)),
                ),
                tap(({ submissionState }) => {
                    this.isBuilding = submissionState === ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION;
                }),
            )
            .subscribe();
    }
}
