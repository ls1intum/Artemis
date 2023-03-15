import { Component, Input, OnInit } from '@angular/core';

import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';

@Component({
    selector: 'jhi-exercise-detail-statistics',
    templateUrl: './exercise-detail-statistics.component.html',
})
export class ExerciseDetailStatisticsComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() doughnutStats: ExerciseManagementStatisticsDto;
    @Input() exerciseType: ExerciseType;

    course: Course;

    readonly DoughnutChartType = DoughnutChartType;

    ngOnInit() {
        this.course = getCourseFromExercise(this.exercise)!;
    }
}
