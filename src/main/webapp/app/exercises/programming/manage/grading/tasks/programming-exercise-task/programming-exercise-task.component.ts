import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExerciseTaskService } from '../programming-exercise-task.service';

@Component({
    selector: 'jhi-programming-exercise-task',
    templateUrl: './programming-exercise-task.component.html',
    styleUrls: ['./programming-exercise-task.component.scss'],
})
export class ProgrammingExerciseTaskComponent implements OnInit {
    @Input() task: ProgrammingExerciseTask;
    @Input() open: boolean;

    @Output() updateTasksEvent = new EventEmitter<void>();

    // Icons
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;

    readonly NOT_ASSIGNED_TO_TASK_NAME = 'Not assigned to task';

    testCaseVisibilityList = Object.entries(Visibility).map(([name, value]) => ({ value, name }));

    get numParticipations(): number {
        return this.programmingExerciseTaskService?.gradingStatistics?.numParticipations ?? 0;
    }

    constructor(private programmingExerciseTaskService: ProgrammingExerciseTaskService) {}

    ngOnInit(): void {
        // If this is the only task have it open by default
        if (this.programmingExerciseTaskService.currentTasks.length == 1) {
            this.open = true;
        }
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
}
