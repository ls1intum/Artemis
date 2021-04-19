import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseStatisticsDTO } from 'app/exercises/text/manage/statistics/exercise-statistics-dto';

@Component({
    selector: 'jhi-text-exercise-management-statistics',
    templateUrl: './text-exercise-statistics.component.html',
})
export class TextExerciseStatisticsComponent implements OnInit {
    // html properties
    SpanType = SpanType;
    graph = Graphs;
    graphTypes = [
        Graphs.SUBMISSIONS,
        Graphs.ACTIVE_USERS,
        Graphs.ACTIVE_TUTORS,
        Graphs.CREATED_RESULTS,
        Graphs.CREATED_FEEDBACKS,
        Graphs.QUESTIONS_ASKED,
        Graphs.QUESTIONS_ANSWERED,
    ];
    currentSpan: SpanType = SpanType.WEEK;
    statisticsView: StatisticsView = StatisticsView.EXERCISE;
    paramSub: Subscription;
    exerciseId: number;

    // Score Distribution

    exerciseStatistics: ExerciseStatisticsDTO;

    constructor(private service: StatisticsService, private route: ActivatedRoute) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.exerciseId = params['exerciseId'];
        });
        this.service.getExerciseStatistics(this.exerciseId).subscribe((res: ExerciseStatisticsDTO) => {
            this.exerciseStatistics = res;
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
