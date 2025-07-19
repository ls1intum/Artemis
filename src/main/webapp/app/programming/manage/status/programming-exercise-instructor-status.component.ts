import { Component, Input, OnChanges, OnDestroy, SimpleChanges, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { findLatestResult } from 'app/shared/util/utils';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { hasParticipationChanged } from 'app/exercise/participation/participation.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

@Component({
    selector: 'jhi-programming-exercise-instructor-status',
    templateUrl: './programming-exercise-instructor-status.component.html',
    imports: [FaIconComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class ProgrammingExerciseInstructorStatusComponent implements OnChanges, OnDestroy {
    private participationWebsocketService = inject(ParticipationWebsocketService);

    ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;

    @Input()
    participationType: ProgrammingExerciseParticipationType;
    @Input()
    participation: SolutionProgrammingExerciseParticipation | TemplateProgrammingExerciseParticipation | Participation;
    @Input()
    exercise: ProgrammingExercise;

    latestResult?: Result;
    resultSubscription: Subscription;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    /**
     * When the participation changes, get the latestResult from it and setup the result subscription for new results.
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges) {
        if (hasParticipationChanged(changes)) {
            this.latestResult = findLatestResult(getAllResultsOfAllSubmissions(this.participation.submissions));
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
            .subscribeForLatestResultOfParticipation(this.participation.id!, false, this.exercise.id)
            .pipe(filter((result) => !!result))
            .subscribe((result) => (this.latestResult = result));
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
