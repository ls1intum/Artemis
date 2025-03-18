import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { Exercise, getCourseFromExercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercise/exercise.service';
import { HttpResponse } from '@angular/common/http';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseDetailStatisticsComponent } from './exercise-detail-statistics.component';
import { StatisticsScoreDistributionGraphComponent } from 'app/shared/statistics-graph/statistics-score-distribution-graph.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exercise-statistics',
    templateUrl: './exercise-statistics.component.html',
    styleUrls: ['../../course/manage/course-management-statistics.component.scss'],
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

    exercise: Exercise;
    course: Course;
    exerciseStatistics: ExerciseManagementStatisticsDto;

    ngOnInit() {
        let exerciseId = 0;
        this.paramSub = this.route.params.subscribe((params) => {
            exerciseId = params['exerciseId'];
        });
        this.exerciseService.find(exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.exercise = exerciseResponse.body!;
            this.course = getCourseFromExercise(this.exercise)!;
        });
        this.service.getExerciseStatistics(exerciseId).subscribe((res: ExerciseManagementStatisticsDto) => {
            this.exerciseStatistics = res;
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
