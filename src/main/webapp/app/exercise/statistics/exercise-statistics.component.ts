import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { Graphs, SpanType, StatisticsView } from 'app/exercise/shared/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/exercise/statistics-graph/service/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { Exercise, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { HttpResponse } from '@angular/common/http';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseDetailStatisticsComponent } from './exercise-detail-statistic/exercise-detail-statistics.component';
import { StatisticsScoreDistributionGraphComponent } from 'app/exercise/statistics-graph/score-distribution-graph/statistics-score-distribution-graph.component';
import { StatisticsGraphComponent } from 'app/exercise/statistics-graph/statistics-graph.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exercise-statistics',
    templateUrl: './exercise-statistics.component.html',
    styleUrls: ['../../course/manage/statistics/course-management-statistics.component.scss'],
    imports: [TranslateDirective, ExerciseDetailStatisticsComponent, StatisticsScoreDistributionGraphComponent, StatisticsGraphComponent, ArtemisTranslatePipe],
})
export class ExerciseStatisticsComponent implements OnInit {
    private service = inject(StatisticsService);
    private route = inject(ActivatedRoute);
    private exerciseService = inject(ExerciseService);

    // html properties
    SpanType = SpanType;
    graphTypes = [Graphs.SUBMISSIONS, Graphs.ACTIVE_USERS, Graphs.ACTIVE_TUTORS, Graphs.CREATED_RESULTS, Graphs.CREATED_FEEDBACKS];
    currentSpan: SpanType = SpanType.WEEK;
    statisticsView: StatisticsView = StatisticsView.EXERCISE;
    paramSub: Subscription;

    readonly exercise = signal<Exercise | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);
    readonly exerciseStatistics = signal<ExerciseManagementStatisticsDto>(undefined!);

    ngOnInit() {
        let exerciseId = 0;
        this.paramSub = this.route.params.subscribe((params) => {
            exerciseId = params['exerciseId'];
        });
        this.exerciseService.find(exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            const exercise = exerciseResponse.body!;
            this.exercise.set(exercise);
            this.course.set(getCourseFromExercise(exercise)!);
        });
        this.service.getExerciseStatistics(exerciseId).subscribe((res: ExerciseManagementStatisticsDto) => {
            this.exerciseStatistics.set(res);
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
