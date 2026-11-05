import { Component, OnDestroy, computed, effect, inject, input, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { findLatestResult } from 'app/foundation/util/utils';
import { faCheckCircle, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { programmingExerciseResultWarning } from './programming-exercise-result-warning.util';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';

/**
 * Describes programming exercise issues
 */
enum ProgrammingExerciseIssues {
    TEMPLATE_PASSING = 'TEMPLATE_PASSING',
    SOLUTION_FAILING = 'SOLUTION_FAILING',
}

@Component({
    selector: 'jhi-programming-exercise-instructor-exercise-status',
    templateUrl: './programming-exercise-instructor-exercise-status.component.html',
    imports: [FaIconComponent, TumAetUiTooltipDirective, TranslateDirective],
})
export class ProgrammingExerciseInstructorExerciseStatusComponent implements OnDestroy {
    private participationWebsocketService = inject(ParticipationWebsocketService);

    ProgrammingExerciseIssues = ProgrammingExerciseIssues;
    readonly templateParticipation = input<Participation>();
    readonly solutionParticipation = input<Participation>();
    readonly exercise = input<ProgrammingExercise>();

    templateParticipationSubscription?: Subscription;
    solutionParticipationSubscription?: Subscription;
    private readonly latestTemplateResult = signal<Result | undefined>(undefined);
    private readonly latestSolutionResult = signal<Result | undefined>(undefined);
    protected readonly issues = computed(() =>
        [
            programmingExerciseResultWarning(this.latestTemplateResult(), ProgrammingExerciseParticipationType.TEMPLATE) ? ProgrammingExerciseIssues.TEMPLATE_PASSING : undefined,
            programmingExerciseResultWarning(this.latestSolutionResult(), ProgrammingExerciseParticipationType.SOLUTION) ? ProgrammingExerciseIssues.SOLUTION_FAILING : undefined,
        ].filter((issue): issue is ProgrammingExerciseIssues => issue !== undefined),
    );
    protected readonly buildsSuccessful = computed(() => this.latestTemplateResult()?.score === 0 && this.latestSolutionResult()?.score === 100 && this.issues().length === 0);

    private subscribedTemplateParticipationId?: number;
    private subscribedSolutionParticipationId?: number;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faCheckCircle = faCheckCircle;

    constructor() {
        effect(() => {
            const participation = this.templateParticipation();
            this.latestTemplateResult.set(findLatestResult(getAllResultsOfAllSubmissions(participation?.submissions)));
            if (this.subscribedTemplateParticipationId === participation?.id) {
                return;
            }
            this.subscribedTemplateParticipationId = participation?.id;
            this.templateParticipationSubscription?.unsubscribe();
            if (participation?.id !== undefined) {
                this.templateParticipationSubscription = this.participationWebsocketService
                    .subscribeForLatestResultOfParticipation(participation.id, false, this.exercise()?.id)
                    .pipe(filter((result) => !!result))
                    .subscribe((result) => this.latestTemplateResult.set(result));
            }
        });
        effect(() => {
            const participation = this.solutionParticipation();
            this.latestSolutionResult.set(findLatestResult(getAllResultsOfAllSubmissions(participation?.submissions)));
            if (this.subscribedSolutionParticipationId === participation?.id) {
                return;
            }
            this.subscribedSolutionParticipationId = participation?.id;
            this.solutionParticipationSubscription?.unsubscribe();
            if (participation?.id !== undefined) {
                this.solutionParticipationSubscription = this.participationWebsocketService
                    .subscribeForLatestResultOfParticipation(participation.id, false, this.exercise()?.id)
                    .pipe(filter((result) => !!result))
                    .subscribe((result) => this.latestSolutionResult.set(result));
            }
        });
    }

    ngOnDestroy(): void {
        this.templateParticipationSubscription?.unsubscribe();
        this.solutionParticipationSubscription?.unsubscribe();
    }
}
