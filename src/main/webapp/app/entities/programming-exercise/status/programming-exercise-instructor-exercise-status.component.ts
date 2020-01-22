import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { Subscription } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { hasSolutionParticipationChanged, hasTemplateParticipationChanged, Participation } from 'app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

enum ProgrammingExerciseIssues {
    TEMPLATE_PASSING = 'TEMPLATE_PASSING',
    SOLUTION_FAILING = 'SOLUTION_FAILING',
}

@Component({
    selector: 'jhi-programming-exercise-instructor-exercise-status',
    templateUrl: './programming-exercise-instructor-exercise-status.component.html',
})
export class ProgrammingExerciseInstructorExerciseStatusComponent implements OnChanges {
    ProgrammingExerciseIssues = ProgrammingExerciseIssues;
    @Input() templateParticipation: Participation;
    @Input() solutionParticipation: Participation;
    @Input() exercise: ProgrammingExercise;

    templateParticipationSubscription: Subscription;
    solutionParticipationSubscription: Subscription;
    issues: (ProgrammingExerciseIssues | null)[] = [];

    constructor(private participationWebsocketService: ParticipationWebsocketService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (hasTemplateParticipationChanged(changes)) {
            if (this.templateParticipationSubscription) {
                this.templateParticipationSubscription.unsubscribe();
            }
            this.templateParticipationSubscription = this.participationWebsocketService
                .subscribeForLatestResultOfParticipation(this.templateParticipation.id)
                .pipe(
                    filter(result => !!result),
                    tap(result => (this.templateParticipation.results = [result!])),
                    tap(() => this.findIssues()),
                )
                .subscribe();
        }

        if (hasSolutionParticipationChanged(changes)) {
            if (this.solutionParticipationSubscription) {
                this.solutionParticipationSubscription.unsubscribe();
            }
            this.solutionParticipationSubscription = this.participationWebsocketService
                .subscribeForLatestResultOfParticipation(this.solutionParticipation.id)
                .pipe(
                    filter(result => !!result),
                    tap(result => (this.solutionParticipation.results = [result!])),
                    tap(() => this.findIssues()),
                )
                .subscribe();
        }

        this.findIssues();
    }

    findIssues() {
        const newestTemplateParticipationResult = this.templateParticipation.results ? this.templateParticipation.results.reduce((acc, x) => (acc.id > x.id ? acc : x)) : null;
        const newestSolutionParticipationResult = this.solutionParticipation.results ? this.solutionParticipation.results.reduce((acc, x) => (acc.id > x.id ? acc : x)) : null;

        this.issues = [
            newestTemplateParticipationResult && newestTemplateParticipationResult.score > 0 ? ProgrammingExerciseIssues.TEMPLATE_PASSING : null,
            newestSolutionParticipationResult && !newestSolutionParticipationResult.successful ? ProgrammingExerciseIssues.SOLUTION_FAILING : null,
        ].filter(Boolean);
    }
}
