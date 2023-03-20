import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTask } from './programming-exercise-task';

@Component({
    selector: 'jhi-configure-grading-task',
    templateUrl: './configure-grading-tasks.component.html',
    styleUrls: ['./configure-grading-tasks.scss'],
    providers: [ProgrammingExerciseTaskService],
})
export class ConfigureGradingTasksComponent implements OnInit {
    @Input() exercise: ProgrammingExercise;
    @Input() course: Course;
    @Input() gradingStatistics: ProgrammingExerciseGradingStatistics;

    faQuestionCircle = faQuestionCircle;
    isSaving = false;
    tasks: ProgrammingExerciseTask[];
    showInactiveTestCases = false;

    constructor(private taskService: ProgrammingExerciseTaskService) {}

    ngOnInit(): void {
        this.taskService.configure(this.exercise, this.course, this.gradingStatistics).subscribe((tasks) => {
            this.tasks = tasks;
        });
    }

    updateTasks() {
        this.tasks = this.taskService.tasks;

        if (!this.showInactiveTestCases) {
            this.tasks = this.tasks.filter((task) => {
                task.testCases = task.testCases.filter((test) => test.active);
                return task.testCases.length;
            });
        }
    }

    toggleShowInactiveTestsShown() {
        this.showInactiveTestCases = !this.showInactiveTestCases;
        this.updateTasks();
    }

    saveTestCases() {
        this.isSaving = true;
        this.taskService.saveTestCases().subscribe(() => (this.isSaving = false));
    }

    resetTestCases() {
        this.isSaving = true;
        this.taskService.resetTestCases().subscribe(() => (this.isSaving = false));
    }
}
