import { StatisticsSpanSelectorComponent } from 'app/exercise/statistics/statistics-span-selector/statistics-span-selector.component';
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
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseDetailStatisticsComponent } from './exercise-detail-statistic/exercise-detail-statistics.component';
import { StatisticsScoreDistributionGraphComponent } from 'app/exercise/statistics-graph/score-distribution-graph/statistics-score-distribution-graph.component';
import { StatisticsGraphComponent } from 'app/exercise/statistics-graph/statistics-graph.component';

@Component({
    selector: 'jhi-exercise-statistics',
    templateUrl: './exercise-statistics.component.html',
    styleUrls: ['../../course/manage/statistics/course-management-statistics.component.scss'],
    imports: [StatisticsSpanSelectorComponent, TranslateDirective, ExerciseDetailStatisticsComponent, StatisticsScoreDistributionGraphComponent, StatisticsGraphComponent],
})
export class ExerciseStatisticsComponent implements OnInit {
    private service = inject(StatisticsService);
    private route = inject(ActivatedRoute);
    private exerciseService = inject(ExerciseService);
    private accountService = inject(AccountService);

    // html properties
    SpanType = SpanType;
    graphTypes = [Graphs.SUBMISSIONS, Graphs.ACTIVE_USERS, Graphs.ACTIVE_TUTORS, Graphs.CREATED_RESULTS, Graphs.CREATED_FEEDBACKS];
    readonly currentSpan = signal<SpanType>(SpanType.WEEK);
    statisticsView: StatisticsView = StatisticsView.EXERCISE;
    paramSub?: Subscription;

    readonly exercise = signal<Exercise | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);
    readonly exerciseStatistics = signal<ExerciseManagementStatisticsDto>(undefined!);

    ngOnInit() {
        let exam = false;
        let exerciseId = 0;
        let courseId = 0;
        this.paramSub = this.route.params.subscribe((params) => {
            exerciseId = Number(params['exerciseId']);
            courseId = Number(params['courseId']);
            exam = !!params['examId'];
        });
        const exerciseSource = exam
            ? this.accountService.isAtLeastEditorInCourseWithId(courseId)
                ? this.exerciseService.findForExamManagement(exerciseId)
                : this.exerciseService.findForExamAssessment(exerciseId)
            : this.exerciseService.findForStaff(exerciseId);
        exerciseSource.subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            const exercise = exerciseResponse.body!;
            this.exercise.set(exercise);
            this.course.set(getCourseFromExercise(exercise));
        });
        this.service.getExerciseStatistics(exerciseId).subscribe((res: ExerciseManagementStatisticsDto) => {
            this.exerciseStatistics.set(res);
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan.set(span);
    }
}
