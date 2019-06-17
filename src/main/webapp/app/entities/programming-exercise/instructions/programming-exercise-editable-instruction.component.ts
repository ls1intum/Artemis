import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { compose, filter, map, sortBy } from 'lodash/fp';
import { Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Participation } from 'app/entities/participation';
import { ProgrammingExercise } from '../programming-exercise.model';
import { DomainCommand } from 'app/markdown-editor/domainCommands';
import { TaskCommand } from 'app/markdown-editor/domainCommands/programming-exercise/task.command';
import { TestCaseCommand } from 'app/markdown-editor/domainCommands/programming-exercise/testCase.command';
import { MarkdownEditorHeight } from 'app/markdown-editor';
import { hasExerciseChanged } from 'app/entities/exercise';
import { ProgrammingExerciseTestCaseService } from 'app/entities/programming-exercise/services';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';

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

    testCaseSubscription: Subscription;

    @Input()
    get participation() {
        return this.participationValue;
    }
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

    constructor(private programmingExerciseTestCaseService: ProgrammingExerciseTestCaseService) {}

    ngOnChanges(changes: SimpleChanges): void {
        // It is possible that the exercise does not have an id in case it is being created now.
        if (hasExerciseChanged(changes) && this.exercise.id) {
            if (this.testCaseSubscription) {
                this.testCaseSubscription.unsubscribe();
            }

            this.testCaseSubscription = this.programmingExerciseTestCaseService
                .subscribeForTestCases(this.exercise.id)
                .pipe(tap(this.setTestCasesFromResult))
                .subscribe();
        }
    }

    ngOnDestroy(): void {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }
    }

    updateProblemStatement(problemStatement: string) {
        this.exercise = { ...this.exercise, problemStatement };
    }

    setTestCasesFromResult = (testCases: ProgrammingExerciseTestCase[]) => {
        // If the exercise is created, there is no result available
        this.exerciseTestCases = compose(
            map(({ testName }) => testName),
            filter(({ active }) => active),
            sortBy('testName'),
        )(testCases);
        this.testCaseCommand.setValues(this.exerciseTestCases);
    };
}
