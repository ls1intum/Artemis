import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-exercise-statistics',
    templateUrl: './exercise-statistics.component.html',
})
export class ExerciseStatisticsComponent implements OnInit {
    // html properties
    SpanType = SpanType;
    graph = Graphs;
    graphTypes = [Graphs.SUBMISSIONS, Graphs.ACTIVE_USERS, Graphs.ACTIVE_TUTORS, Graphs.CREATED_RESULTS, Graphs.CREATED_FEEDBACKS];
    currentSpan: SpanType = SpanType.WEEK;
    statisticsView: StatisticsView = StatisticsView.EXERCISE;
    paramSub: Subscription;
    exerciseId: number;

    exerciseStatistics: ExerciseManagementStatisticsDto;

    // doughnut chart percentage values
    participationsInPercent = 0;
    questionsAnsweredInPercent = 0;
    absoluteAveragePoints = 0;

    constructor(private service: StatisticsService, private route: ActivatedRoute) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.exerciseId = params['exerciseId'];
        });
        this.service.getExerciseStatistics(this.exerciseId).subscribe((res: ExerciseManagementStatisticsDto) => {
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
