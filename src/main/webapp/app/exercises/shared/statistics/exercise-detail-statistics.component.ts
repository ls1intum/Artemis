import { Component, Input } from '@angular/core';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exercise-detail-statistics',
    templateUrl: './exercise-detail-statistics.component.html',
})
export class ExerciseDetailStatisticsComponent {
    @Input() exercise: Exercise;
    @Input() doughnutStats: ExerciseManagementStatisticsDto;
    @Input() exerciseType: ExerciseType;

    readonly DoughnutChartType = DoughnutChartType;
}
