import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { findLatestResult } from 'app/foundation/util/utils';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

@Component({
    selector: 'jhi-programming-exercise-instructor-status',
    templateUrl: './programming-exercise-instructor-status.component.html',
    imports: [FaIconComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class ProgrammingExerciseInstructorStatusComponent implements OnDestroy {
    private participationWebsocketService = inject(ParticipationWebsocketService);

    ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;

    readonly participationType = input<ProgrammingExerciseParticipationType>();
    readonly participation = input<SolutionProgrammingExerciseParticipation | TemplateProgrammingExerciseParticipation | Participation>();
    readonly exercise = input<ProgrammingExercise>();

    protected readonly latestResult = signal<Result | undefined>(undefined);
    resultSubscription: Subscription;

    // Tracks the id of the participation the current subscription belongs to, so that the
    // subscription is only re-created when the participation actually changes (mirrors the
    // previous hasParticipationChanged guard, which also reacted to the first undefined -> value change).
    private subscribedParticipationId?: number;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    constructor() {
        // When the participation changes, get the latestResult from it and setup the result subscription for new results.
        effect(() => {
            const participation = this.participation();
            if (!participation) {
                return;
            }
            if (this.subscribedParticipationId === participation.id) {
                return;
            }
            this.subscribedParticipationId = participation.id;
            this.latestResult.set(findLatestResult(getAllResultsOfAllSubmissions(participation.submissions)));
            this.updateResultSubscription();
        });
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
            .subscribeForLatestResultOfParticipation(this.participation()!.id!, false, this.exercise()?.id)
            .pipe(filter((result) => !!result))
            .subscribe((result) => this.latestResult.set(result));
    }

    /**
     * If there is an existing subscription, unsubscribe.
     */
    ngOnDestroy(): void {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
    }
}
