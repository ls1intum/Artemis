import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ParticipationType, ProgrammingExercise } from 'app/entities/programming-exercise';
import { hasParticipationChanged, Participation } from 'app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { Result } from 'app/entities/result';

@Component({
    selector: 'jhi-programming-exercise-instructor-status',
    templateUrl: './programming-exercise-instructor-status.component.html',
})
export class ProgrammingExerciseInstructorStatusComponent implements OnChanges, OnDestroy {
    ParticipationType = ParticipationType;

    @Input()
    participationType: ParticipationType;
    @Input()
    participation: Participation;
    @Input()
    exercise: ProgrammingExercise;

    latestResult: Result | null;
    resultSubscription: Subscription;

    constructor(private participationWebsocketService: ParticipationWebsocketService) {}

    /**
     * When the participation changes, get the latestResult from it and setup the result subscription for new results.
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges) {
        if (hasParticipationChanged(changes)) {
            this.latestResult = this.participation.results.length ? this.participation.results.reduce((currentMax, next) => (next.id > currentMax.id ? next : currentMax)) : null;
            this.updateResultSubscription();
        }
    }

    /**
     * If there is an existing subscription, unsubscribe.
     * Create a new subscription for the provided participation.
     */
    updateResultSubscription() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }

        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id)
            .pipe(filter(result => !!result))
            .subscribe(result => (this.latestResult = result));
    }

    ngOnDestroy(): void {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
    }
}
