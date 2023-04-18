import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleRight, faAsterisk, faMedal, faQuestionCircle, faScaleUnbalanced } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTask } from './programming-exercise-task';
import { Observable, Subject } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-grading-task',
    templateUrl: './programming-exercise-grading-task.component.html',
    styleUrls: ['./programming-exercise-grading-task.scss'],
})
export class ProgrammingExerciseGradingTaskComponent implements OnInit {
    @Input() exercise: ProgrammingExercise;
    @Input() course: Course;
    @Input() gradingStatisticsObservable: Observable<ProgrammingExerciseGradingStatistics>;

    // Icons
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;
    faQuestionCircle = faQuestionCircle;
    faScaleUnbalanced = faScaleUnbalanced;
    faMedal = faMedal;
    faAsterisk = faAsterisk;

    isSaving = false;
    tasks: ProgrammingExerciseTask[];
    allTasksExpandedSubject: Subject<boolean>;

    get ignoreInactive() {
        return this.taskService.ignoreInactive;
    }

    constructor(private taskService: ProgrammingExerciseTaskService) {}

    ngOnInit(): void {
        this.allTasksExpandedSubject = new Subject();
        this.gradingStatisticsObservable.subscribe((gradingStatistics) => {
            this.taskService.configure(this.exercise, this.course, gradingStatistics).subscribe(this.updateTasks);
        });
    }

    updateTasks = () => {
        this.tasks = this.taskService.updateTasks();
    };

    toggleShowInactiveTestsShown = () => {
        this.taskService.toggleIgnoreInactive();
        this.updateTasks();
    };

    saveTestCases = () => {
        this.isSaving = true;
        this.taskService.saveTestCases().subscribe(() => (this.isSaving = false));
    };

    resetTestCases = () => {
        this.isSaving = true;
        this.taskService.resetTestCases().subscribe(() => {
            this.isSaving = false;
            this.updateTasks();
        });
    };

    toggleAllTasksExpanded = (value: boolean) => {
        this.allTasksExpandedSubject.next(value);
    };
}
