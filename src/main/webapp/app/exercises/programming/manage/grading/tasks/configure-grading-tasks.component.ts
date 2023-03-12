import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-configure-grading-task',
    templateUrl: './configure-grading-tasks.component.html',
    styleUrls: ['./configure-grading-tasks.scss'],
    providers: [ProgrammingExerciseTaskService],
})
export class ConfigureGradingTasksComponent implements OnInit {
    @Input() exercise: ProgrammingExercise;
    @Input() course: Course;

    faQuestionCircle = faQuestionCircle;

    constructor(private taskService: ProgrammingExerciseTaskService) {}

    get tasks() {
        return this.taskService.tasks;
    }

    ngOnInit(): void {
        this.taskService.configure(this.exercise, this.course);
    }
}
