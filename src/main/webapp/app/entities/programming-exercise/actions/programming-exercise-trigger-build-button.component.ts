import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { filter, take, tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services';
import { ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';
import { hasParticipationChanged, Participation, ParticipationWebsocketService } from 'app/entities/participation';

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
    @Input() btnSize = ButtonSize.SMALL;

    participationHasResult: boolean;
    isBuilding: boolean;

    private submissionSubscription: Subscription;
    private resultSubscription: Subscription;

    constructor(private participationWebsocketService: ParticipationWebsocketService, protected submissionService: ProgrammingSubmissionWebsocketService) {}

    /**
     * Check if the participation has changed, if so set up the websocket connections.
     *
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            this.participationHasResult = !!(this.participation.results && this.participation.results.length);
            if (!this.participationHasResult) {
                this.setupResultSubscription();
            }
            this.setupSubmissionSubscription();
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

    /*    triggerBuild(event: any) {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        this.participationService.triggerBuild(this.participation.id).subscribe();
    }*/
}
