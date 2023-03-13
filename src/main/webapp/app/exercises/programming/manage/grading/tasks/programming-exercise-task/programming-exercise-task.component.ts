import { Component, Input } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { Visibility } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExerciseTaskService } from '../programming-exercise-task.service';

@Component({
    selector: 'jhi-programming-exercise-task',
    templateUrl: './programming-exercise-task.component.html',
    styleUrls: ['./programming-exercise-task.component.scss'],
})
export class ProgrammingExerciseTaskComponent {
    @Input() task: ProgrammingExerciseTask;
    @Input() open: boolean;

    // Icons
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;

    testCaseVisibilityList = Object.entries(Visibility).map(([name, value]) => ({ value, name }));

    constructor(private programmingExerciseTaskService: ProgrammingExerciseTaskService) {}

    testUpdateHandler() {
        this.task = this.programmingExerciseTaskService.updateTask(this.task);
    }

    taskUpdateHandler() {
        const testCasesAmount = this.task.testCases.length;

        this.task.testCases.forEach((testCase) => {
            if (this.task.weight != undefined) {
                testCase.weight = this.task.weight / testCasesAmount;
            }
            if (this.task.bonusMultiplier != undefined) {
                testCase.bonusMultiplier = this.task.bonusMultiplier;
            }
            if (this.task.bonusPoints != undefined) {
                testCase.bonusPoints = this.task.bonusPoints / testCasesAmount;
            }
            if (this.task.visibility != undefined) {
                testCase.visibility = this.task.visibility;
            }
        });

        this.programmingExerciseTaskService.updateTaskPoints(this.task);
    }
}
