import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { compose, map, sortBy } from 'lodash/fp';
import { Subscription, of } from 'rxjs';
import { filter, catchError, tap } from 'rxjs/operators';
import { getLatestResult, hasTemplateParticipationChanged, Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { ProgrammingExercise } from '../programming-exercise.model';
import { Result } from 'app/entities/result';
import { DomainCommand } from 'app/markdown-editor/domainCommands';
import { TaskCommand } from 'app/markdown-editor/domainCommands/programming-exercise/task.command';
import { TestCaseCommand } from 'app/markdown-editor/domainCommands/programming-exercise/testCase.command';
import { MarkdownEditorComponent, MarkdownEditorHeight } from 'app/markdown-editor';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
})
export class ProgrammingExerciseEditableInstructionComponent implements OnChanges, OnDestroy {
    participationValue: Participation;
    exerciseValue: ProgrammingExercise;

    exerciseTestCases: string[] = [];

    taskCommand = new TaskCommand();
    taskRegex = this.taskCommand.getTagRegex('g');
    testCaseCommand = new TestCaseCommand();
    domainCommands: DomainCommand[] = [this.taskCommand, this.testCaseCommand];

    templateResultSubscription: Subscription;

    savingInstructions = false;
    unsavedChanges = false;

    @ViewChild(MarkdownEditorComponent, { static: false }) markdownEditor: MarkdownEditorComponent;

    @Input() showStatus = true;
    @Input() enableSave = true;
    @Input() enableResize = true;
    @Input() showSaveButton = false;
    @Input()
    get participation() {
        return this.participationValue;
    }
    @Input()
    get exercise() {
        return this.exerciseValue;
    }
    @Input() templateParticipation: Participation;
    @Output() participationChange = new EventEmitter<Participation>();

    @Input() markdownEditorHeight = MarkdownEditorHeight.MEDIUM;
    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();

    set exercise(exercise: ProgrammingExercise) {
        if (this.exerciseValue && exercise.problemStatement !== this.exerciseValue.problemStatement) {
            this.unsavedChanges = true;
        }
        this.exerciseValue = exercise;
        this.exerciseChange.emit(this.exerciseValue);
    }

    set participation(participation: Participation) {
        this.participationValue = participation;
        this.participationChange.emit(this.participationValue);
    }

    constructor(
        private participationWebsocketService: ParticipationWebsocketService,
        private programmingExerciseService: ProgrammingExerciseService,
        private jhiAlertService: JhiAlertService,
    ) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (hasTemplateParticipationChanged(changes)) {
            if (this.templateParticipation.results) {
                this.setTestCasesFromResult(getLatestResult(this.templateParticipation)!);
            }
            if (this.templateResultSubscription) {
                this.templateResultSubscription.unsubscribe();
            }

            this.templateResultSubscription = this.participationWebsocketService
                .subscribeForLatestResultOfParticipation(this.templateParticipation.id)
                .pipe(
                    filter(result => !!result),
                    tap(result => {
                        this.templateParticipation.results = [...this.templateParticipation.results, result!];
                    }),
                    tap(this.setTestCasesFromResult),
                )
                .subscribe();
        }
    }

    ngOnDestroy(): void {
        if (this.templateResultSubscription) {
            this.templateResultSubscription.unsubscribe();
        }
    }

    /* Save the problem statement on the server.
     * @param $event
     */
    saveInstructions($event: any) {
        $event.stopPropagation();
        this.savingInstructions = true;
        return this.programmingExerciseService
            .updateProblemStatement(this.exercise.id, this.exercise.problemStatement!)
            .pipe(
                tap(() => {
                    this.unsavedChanges = false;
                }),
                catchError(() => {
                    // TODO: move to programming exercise translations
                    this.jhiAlertService.error(`artemisApp.editor.errors.problemStatementCouldNotBeUpdated`);
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.savingInstructions = false;
            });
    }

    updateProblemStatement(problemStatement: string) {
        if (this.exercise.problemStatement !== problemStatement) {
            this.exercise = { ...this.exercise, problemStatement };
            this.unsavedChanges = true;
        }
    }

    setTestCasesFromResult = (result: Result) => {
        // If the exercise is created, there is no result available
        this.exerciseTestCases =
            result && result.feedbacks
                ? compose(
                      map(({ text }) => text),
                      sortBy('text'),
                  )(result.feedbacks)
                : [];
        this.testCaseCommand.setValues(this.exerciseTestCases);
    };
}
