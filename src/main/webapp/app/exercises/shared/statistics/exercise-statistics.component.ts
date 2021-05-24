import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { round } from 'app/shared/util/utils';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-exercise-statistics',
    templateUrl: './exercise-statistics.component.html',
})
export class ExerciseStatisticsComponent implements OnInit {
    readonly DoughnutChartType = DoughnutChartType;
    readonly ExerciseType = ExerciseType;

    // html properties
    SpanType = SpanType;
    graph = Graphs;
    graphTypes = [Graphs.SUBMISSIONS, Graphs.ACTIVE_USERS, Graphs.ACTIVE_TUTORS, Graphs.CREATED_RESULTS, Graphs.CREATED_FEEDBACKS];
    currentSpan: SpanType = SpanType.WEEK;
    statisticsView: StatisticsView = StatisticsView.EXERCISE;
    paramSub: Subscription;

    exercise: Exercise;
    exerciseStatistics: ExerciseManagementStatisticsDto;

    // doughnut chart percentage values
    participationsInPercent = 0;
    questionsAnsweredInPercent = 0;
    absoluteAveragePoints = 0;

    constructor(private service: StatisticsService, private route: ActivatedRoute, private exerciseService: ExerciseService) {}

    ngOnInit() {
        let exerciseId = 0;
        this.paramSub = this.route.params.subscribe((params) => {
            exerciseId = params['exerciseId'];
        });
        this.exerciseService.find(exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.exercise = exerciseResponse.body!;
        });
        this.service.getExerciseStatistics(exerciseId).subscribe((res: ExerciseManagementStatisticsDto) => {
            this.exerciseStatistics = res;
            this.participationsInPercent = res.numberOfStudentsInCourse > 0 ? round((res.numberOfParticipations / res.numberOfStudentsInCourse) * 100, 1) : 0;
            this.questionsAnsweredInPercent = res.numberOfQuestions > 0 ? round((res.numberOfAnsweredQuestions / res.numberOfQuestions) * 100, 1) : 0;
            this.absoluteAveragePoints = round((res.averageScoreOfExercise * res.maxPointsOfExercise) / 100, 1);
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
