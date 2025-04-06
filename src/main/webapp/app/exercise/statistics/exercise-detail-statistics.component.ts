import { Component, Input, OnInit } from '@angular/core';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { DoughnutChartType } from 'app/core/course/manage/detail/course-detail.component';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course, isCommunicationEnabled } from 'app/core/course/shared/entities/course.model';
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
