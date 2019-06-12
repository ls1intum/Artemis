import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { compose, map, sortBy } from 'lodash/fp';
import { Subscription } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { getLatestResult, hasTemplateParticipationChanged, Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { ProgrammingExercise } from '../programming-exercise.model';
import { Result } from 'app/entities/result';
import { DomainCommand } from 'app/markdown-editor/domainCommands';
import { TaskCommand } from 'app/markdown-editor/domainCommands/programming-exercise/task.command';
import { TestCaseCommand } from 'app/markdown-editor/domainCommands/programming-exercise/testCase.command';
import { MarkdownEditorHeight } from 'app/markdown-editor';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
    styleUrls: ['./programming-exercise-editable-instruction.scss'],
})
export class ProgrammingExerciseEditableInstructionComponent implements OnChanges, OnDestroy {
    participationValue: Participation;
    exerciseValue: ProgrammingExercise;

    exerciseTestCases: string[] = [];

    taskCommand = new TaskCommand();
    taskRegex = this.taskCommand.getTagRegex('g');
    testCaseCommand = new TestCaseCommand();
    domainCommands: DomainCommand[] = [this.taskCommand, this.testCaseCommand];

    resultSubscription: Subscription;
    templateResultSubscription: Subscription;

    @Input()
    get participation() {
        return this.participationValue;
    }
    @Input() templateParticipation: Participation;
    @Output() participationChange = new EventEmitter<Participation>();

    set participation(participation: Participation) {
        this.participationValue = participation;
        this.participationChange.emit(this.participationValue);
    }

    @Input()
    get exercise() {
        return this.exerciseValue;
    }
    @Input() markdownEditorHeight = MarkdownEditorHeight.SMALL;

    @Output()
    exerciseChange = new EventEmitter<ProgrammingExercise>();

    set exercise(exercise: ProgrammingExercise) {
        this.exerciseValue = exercise;
        this.exerciseChange.emit(this.exerciseValue);
    }

    constructor(private participationWebsocketService: ParticipationWebsocketService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (hasTemplateParticipationChanged(changes)) {
            if (this.templateParticipation.results) {
                this.setTestCasesFromResult(getLatestResult(this.templateParticipation));
            }
            if (this.templateResultSubscription) {
                this.resultSubscription.unsubscribe();
            }

            this.templateResultSubscription = this.participationWebsocketService
                .subscribeForLatestResultOfParticipation(this.templateParticipation.id)
                .pipe(
                    filter(result => !!result),
                    tap(result => {
                        this.templateParticipation.results = [...this.templateParticipation.results, result];
                    }),
                    tap(this.setTestCasesFromResult),
                )
                .subscribe();
        }

        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }

        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id)
            .pipe(
                filter(result => !!result),
                tap(result => {
                    this.participation.results = [...this.participation.results, result];
                }),
            )
            .subscribe();
    }

    ngOnDestroy(): void {
        if (this.templateParticipation.results) {
            this.setTestCasesFromResult(getLatestResult(this.templateParticipation));
        }
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
    }

    updateProblemStatement(problemStatement: string) {
        this.exercise = { ...this.exercise, problemStatement };
    }

    setTestCasesFromResult(result: Result) {
        // If the exercise is created, there is no result available
        this.exerciseTestCases =
            result && result.feedbacks
                ? compose(
                      map(({ text }) => text),
                      sortBy('text'),
                  )(result.feedbacks)
                : [];
        this.testCaseCommand.setValues(this.exerciseTestCases);
    }
}
