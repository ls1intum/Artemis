import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { orderBy as _orderBy } from 'lodash';
import { Subscription, of } from 'rxjs';
import { catchError, filter, map, tap } from 'rxjs/operators';
import { hasParticipationChanged, Participation } from 'app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { Result, ResultService } from '.';
import { RepositoryService } from 'app/entities/repository/repository.service';

import * as moment from 'moment';
import { ExerciseType } from 'app/entities/exercise';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';

@Component({
    selector: 'jhi-updating-result',
    templateUrl: './updating-result.component.html',
    providers: [ResultService, RepositoryService],
})

/**
 * A component that wraps the result component, updating its result on every websocket result event for the logged in user.
 * If the participation changes, the newest result from its result array will be used.
 * If the participation does not have any results, there will be no result displayed, until a new result is received through the websocket.
 */
export class UpdatingResultComponent implements OnChanges, OnDestroy {
    @Input() exerciseType: ExerciseType;
    @Input() participation: Participation;
    @Input() short = false;
    @Input() result: Result | null;
    @Input() showUngradedResults: boolean;
    @Input() showGradedBadge: boolean;

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
            if (this.exerciseType === ExerciseType.PROGRAMMING) {
                this.subscribeForNewSubmissions();
            }
        }
    }

    ngOnDestroy() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        if (this.submissionSubscription) {
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
                filter((result: Result) => this.showUngradedResults || result.rated === true),
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
            .getLatestPendingSubmissionByParticipationId(this.participation.id)
            .pipe(tap(([, pendingSubmission]) => (this.isBuilding = !!pendingSubmission)))
            .subscribe();
    }
}
