import { Component, Input } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { Visibility } from 'app/entities/programming-exercise-test-case.model';

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
    taskVisibilityList = [...this.testCaseVisibilityList, { value: 'MIXED', name: 'Mixed' }];
}
