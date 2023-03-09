import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-configure-grading-task',
    templateUrl: './configure-grading-tasks.component.html',
    styleUrls: ['./configure-grading-tasks.scss'],
})
export class ConfigureGradingTasksComponent implements OnInit {
    @Input() exercise: ProgrammingExercise;

    faQuestionCircle = faQuestionCircle;

    tasks: ProgrammingExerciseTask[];

    constructor(private taskService: ProgrammingExerciseTaskService) {}

    ngOnInit(): void {
        this.taskService.getTasksByExercise(this.exercise).subscribe((serverSideTasks) => {
            this.tasks = serverSideTasks.map((task) => task as ProgrammingExerciseTask).map((task) => this.taskService.updateValues(task));
        });
    }
}
