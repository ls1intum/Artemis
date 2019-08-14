import { Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { catchError, filter, map, switchMap, take, tap } from 'rxjs/operators';
import { Observable, of, Subscription } from 'rxjs';
import { ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';
import { hasParticipationChanged, InitializationState, Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { Result } from 'app/entities/result';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise';

export enum ButtonSize {
    SMALL = 'btn-sm',
    MEDIUM = 'btn-md',
    LARGE = 'btn-lg',
}

/**
 * Component for triggering a build for the CURRENT submission of the student (does not create a new commit!).
 * The participation given as input needs to have the results attached as this component checks if there is at least one result.
 * If there is no result, the button is disabled because this would mean that the student has not made a commit yet.
 */
export abstract class ProgrammingExerciseTriggerBuildButtonComponent implements OnChanges, OnDestroy {
    abstract triggerBuild: (event: any) => void;
    abstract getTooltip: () => string;

    @Input() participation: Participation;
    @Input() showProgress: boolean;
    @Input() btnSize = ButtonSize.SMALL;

    participationHasResult: boolean;
    participationIsActive: boolean;
    isBuilding: boolean;

    private submissionSubscription: Subscription;
    private resultSubscription: Subscription;

    protected constructor(
        private participationWebsocketService: ParticipationWebsocketService,
        protected submissionService: ProgrammingSubmissionWebsocketService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {}

    /**
     * Check if the participation has changed, if so set up the websocket connections.
     *
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            this.participationHasResult = !!this.participation.results;
            this.participationIsActive = this.participation.initializationState === InitializationState.INITIALIZED;
            of(this.participationHasResult)
                .pipe(
                    // Ideally this component is provided a participation with an attached result. If this is not the cased, try retrieve it from the server.
                    switchMap((participationHashResult: boolean) => {
                        return participationHashResult ? of(!!this.participation.results.length) : this.checkIfHasResult(this.participation.id);
                    }),
                    // If there is no result yet for a participation, create a websocket subscription to get the first incoming result.
                    tap((participationHasResult: boolean) => {
                        if (!participationHasResult) {
                            this.setupResultSubscription();
                        }
                    }),
                )
                .subscribe(() => {
                    this.setupSubmissionSubscription();
                });
        }
    }

    ngOnDestroy(): void {
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
    }

    private checkIfHasResult(participationId: number): Observable<boolean> {
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(participationId).pipe(
            catchError(() => of(null)),
            map((result: Result | null) => !!result),
        );
    }

    /**
     * Set up a websocket subscription to pending submissions to set the isBuilding flag.
     * If there is a pending submission, isBuilding is set to true, otherwise to false.
     */
    setupSubmissionSubscription() {
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        this.submissionSubscription = this.submissionService
            .getLatestPendingSubmission(this.participation.id)
            .pipe(tap(submission => (this.isBuilding = !!submission)))
            .subscribe();
    }

    /**
     * Wait for the first result to come in, when it comes in set the boolean flag participationHasResult to true.
     */
    setupResultSubscription() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id)
            .pipe(
                filter(result => !!result),
                take(1),
                tap(() => (this.participationHasResult = true)),
            )
            .subscribe();
    }
}
