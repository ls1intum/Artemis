import { Component, Input, OnInit } from '@angular/core';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { Course, isCommunicationEnabled } from 'app/entities/course.model';
import { DoughnutChartComponent } from './doughnut-chart.component';

@Component({
    selector: 'jhi-exercise-detail-statistics',
    templateUrl: './exercise-detail-statistics.component.html',
    imports: [DoughnutChartComponent],
})
export class ExerciseDetailStatisticsComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() doughnutStats: ExerciseManagementStatisticsDto;
    @Input() exerciseType: ExerciseType;

    course: Course;
    isCommunicationEnabled = isCommunicationEnabled;

    readonly DoughnutChartType = DoughnutChartType;

    ngOnInit() {
        this.course = getCourseFromExercise(this.exercise)!;
    }
}
