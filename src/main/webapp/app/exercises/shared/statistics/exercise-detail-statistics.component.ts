import { Component, Input, OnChanges } from '@angular/core';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-exercise-detail-statistics',
    templateUrl: './exercise-detail-statistics.component.html',
})
export class ExerciseDetailStatisticsComponent implements OnChanges {
    @Input() exercise: Exercise;
    @Input() doughnutStats: ExerciseManagementStatisticsDto;

    readonly DoughnutChartType = DoughnutChartType;
    readonly ExerciseType = ExerciseType;
    absoluteAveragePoints = 0;
    participationsInPercent = 0;
    questionsAnsweredInPercent = 0;

    ngOnChanges(): void {
        if (this.doughnutStats) {
            this.participationsInPercent =
                this.doughnutStats.numberOfStudentsInCourse > 0 ? round((this.doughnutStats.numberOfParticipations / this.doughnutStats.numberOfStudentsInCourse) * 100, 1) : 0;
            this.questionsAnsweredInPercent =
                this.doughnutStats.numberOfQuestions > 0 ? round((this.doughnutStats.numberOfAnsweredQuestions / this.doughnutStats.numberOfQuestions) * 100, 1) : 0;
            this.absoluteAveragePoints = round((this.doughnutStats.averageScoreOfExercise * this.doughnutStats.maxPointsOfExercise) / 100, 1);
        }
    }
}
