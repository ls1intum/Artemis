import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming/programming-exercise-test-case.model';
import { ProgrammingExerciseTaskService } from '../programming-exercise-task.service';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-task',
    templateUrl: './programming-exercise-task.component.html',
    styleUrls: ['../programming-exercise-grading-tasks-table.scss'],
})
export class ProgrammingExerciseTaskComponent implements OnInit {
    @Input() index: number;
    @Input() task: ProgrammingExerciseTask;
    @Input() openSubject: Subject<boolean>;
    @Input() isExamExercise: boolean;

    @Output() updateTasksEvent = new EventEmitter<void>();

    // Icons
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;

    readonly NOT_ASSIGNED_TO_TASK_NAME = 'Not assigned to task';
    open: boolean;
    onlyViewTestCases: boolean;
    testCaseVisibilityList: { value: Visibility; name: string }[] = [];

    get numParticipations(): number {
        return this.programmingExerciseTaskService?.gradingStatistics?.numParticipations ?? 0;
    }

    constructor(private programmingExerciseTaskService: ProgrammingExerciseTaskService) {}

    ngOnInit(): void {
        this.openSubject.subscribe((open) => (this.open = open));

        // If this is the only task have it open by default and hide the task
        if (this.programmingExerciseTaskService.currentTasks.length == 1) {
            this.onlyViewTestCases = true;
            this.open = true;
        }

        this.updateTestCaseVisibilityList();
    }

    testUpdateHandler(test: ProgrammingExerciseTestCase) {
        this.programmingExerciseTaskService.initializeTask(this.task);
        test.changed = true;
        this.updateTasksEvent.emit();
    }

    taskUpdateHandler() {
        const testCasesAmount = this.task.testCases.length;
        const testCasesWeightSum = this.task.testCases.reduce((acc, { weight }) => acc + (weight ?? 0), 0);

        this.task.testCases.forEach((testCase) => {
            testCase.changed = true;

            if (this.task.weight !== undefined && testCasesWeightSum !== 0) {
                testCase.weight = ((testCase.weight ?? 0) / testCasesWeightSum) * this.task.weight;
            }
            if (this.task.bonusMultiplier !== undefined) {
                testCase.bonusMultiplier = this.task.bonusMultiplier;
            }
            if (this.task.bonusPoints !== undefined) {
                testCase.bonusPoints = this.task.bonusPoints / testCasesAmount;
            }
            if (this.task.visibility !== undefined) {
                testCase.visibility = this.task.visibility;
            }
        });

        this.updateTasksEvent.emit();
    }

    formatTestIndex(i: number, j: number): string {
        return `${i + 1}.${j + 1}`;
    }

    private updateTestCaseVisibilityList() {
        this.testCaseVisibilityList = Object.entries(Visibility).map(([name, value]) => {
            let displayName = name;

            if (this.isExamExercise && value === Visibility.AfterDueDate) {
                displayName = 'AfterReleaseDateOfResults';
            }

            return {
                value,
                name: displayName,
            };
        });
    }
}
