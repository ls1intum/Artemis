import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { findLatestResult } from 'app/foundation/util/utils';
import { faCheckCircle, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

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
    imports: [FaIconComponent, NgbTooltip, TranslateDirective],
})
export class ProgrammingExerciseInstructorExerciseStatusComponent implements OnDestroy {
    private participationWebsocketService = inject(ParticipationWebsocketService);

    ProgrammingExerciseIssues = ProgrammingExerciseIssues;
    readonly templateParticipation = input<Participation>();
    readonly solutionParticipation = input<Participation>();
    readonly exercise = input<ProgrammingExercise>();

    templateParticipationSubscription: Subscription;
    solutionParticipationSubscription: Subscription;
    protected readonly issues = signal<(ProgrammingExerciseIssues | undefined)[]>([]);

    // Track the ids of the participations the current subscriptions belong to, so that the
    // subscriptions are only re-created when a participation actually changes (mirrors the previous
    // hasTemplateParticipationChanged / hasSolutionParticipationChanged guards, which also reacted to
    // the first undefined -> value change).
    private subscribedTemplateParticipationId?: number;
    private subscribedSolutionParticipationId?: number;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faCheckCircle = faCheckCircle;

    constructor() {
        // If there are changes for the template or solution participation, check if there are issues
        // for the results of the template or solution participation.
        effect(() => {
            const templateParticipation = this.templateParticipation();
            const solutionParticipation = this.solutionParticipation();
            const exercise = this.exercise();

            if (templateParticipation && this.subscribedTemplateParticipationId !== templateParticipation.id) {
                this.subscribedTemplateParticipationId = templateParticipation.id;
                if (this.templateParticipationSubscription) {
                    this.templateParticipationSubscription.unsubscribe();
                }
                this.templateParticipationSubscription = this.participationWebsocketService
                    .subscribeForLatestResultOfParticipation(templateParticipation.id!, false, exercise?.id)
                    .pipe(
                        filter((result) => !!result),
                        // Read the current participation from the signal at emit time so that a new
                        // instance carrying the same id (which does not trigger a re-subscription)
                        // still receives the update that findIssues() subsequently reads.
                        tap((result) => (this.templateParticipation()!.submissions!.last()!.results = [result!])),
                        tap(() => this.findIssues()),
                    )
                    .subscribe();
            }

            if (solutionParticipation && this.subscribedSolutionParticipationId !== solutionParticipation.id) {
                this.subscribedSolutionParticipationId = solutionParticipation.id;
                if (this.solutionParticipationSubscription) {
                    this.solutionParticipationSubscription.unsubscribe();
                }
                this.solutionParticipationSubscription = this.participationWebsocketService
                    .subscribeForLatestResultOfParticipation(solutionParticipation.id!, false, exercise?.id)
                    .pipe(
                        filter((result) => !!result),
                        // Read the current participation from the signal at emit time so that a new
                        // instance carrying the same id (which does not trigger a re-subscription)
                        // still receives the update that findIssues() subsequently reads.
                        tap((result) => (this.solutionParticipation()!.submissions!.last()!.results = [result!])),
                        tap(() => this.findIssues()),
                    )
                    .subscribe();
            }

            this.findIssues();
        });
    }

    /**
     * Finds issues of the template and the solution participation result
     */
    findIssues() {
        const newestTemplateParticipationResult = findLatestResult(getAllResultsOfAllSubmissions(this.templateParticipation()?.submissions));
        const newestSolutionParticipationResult = findLatestResult(getAllResultsOfAllSubmissions(this.solutionParticipation()?.submissions));

        this.issues.set(
            [
                newestTemplateParticipationResult && newestTemplateParticipationResult.score! > 0 ? ProgrammingExerciseIssues.TEMPLATE_PASSING : undefined,
                newestSolutionParticipationResult && !newestSolutionParticipationResult.successful ? ProgrammingExerciseIssues.SOLUTION_FAILING : undefined,
            ].filter(Boolean),
        );
    }

    ngOnDestroy(): void {
        this.templateParticipationSubscription?.unsubscribe();
        this.solutionParticipationSubscription?.unsubscribe();
    }
}
