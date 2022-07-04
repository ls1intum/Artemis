import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-exercise-statistics',
    templateUrl: './exercise-statistics.component.html',
    styleUrls: ['../../../course/manage/course-management-statistics.component.scss'],
})
export class ExerciseStatisticsComponent implements OnInit {
    // html properties
    SpanType = SpanType;
    graphTypes = [Graphs.SUBMISSIONS, Graphs.ACTIVE_USERS, Graphs.ACTIVE_TUTORS, Graphs.CREATED_RESULTS, Graphs.CREATED_FEEDBACKS];
    currentSpan: SpanType = SpanType.WEEK;
    statisticsView: StatisticsView = StatisticsView.EXERCISE;
    paramSub: Subscription;

    exercise: Exercise;
    exerciseStatistics: ExerciseManagementStatisticsDto;

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
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
