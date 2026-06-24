import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { Subscription } from 'rxjs';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { MathExerciseService } from '../service/math-exercise.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/exercise/statistics-graph/service/statistics.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { DetailOverviewSection, DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import {
    getExerciseGeneralDetailsSection,
    getExerciseGradingDefaultDetails,
    getExerciseMarkdownSolution,
    getExerciseModeDetailSection,
    getExerciseProblemDetailSection,
} from 'app/exercise/util/utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { DetailOverviewListComponent } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { DecimalPipe } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

@Component({
    selector: 'jhi-math-exercise-detail',
    templateUrl: './math-exercise-detail.component.html',
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        NonProgrammingExerciseDetailCommonActionsComponent,
        ExerciseDetailStatisticsComponent,
        DetailOverviewListComponent,
        ArtemisDatePipe,
        DecimalPipe,
        ButtonModule,
        CardModule,
        TableModule,
        TagModule,
    ],
})
export class MathExerciseDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private eventManager = inject(EventManager);
    private artemisMarkdownService = inject(ArtemisMarkdownService);
    private mathExerciseService = inject(MathExerciseService);
    private mathSubmissionService = inject(MathSubmissionService);
    private statisticsService = inject(StatisticsService);

    readonly ExerciseType = ExerciseType;

    readonly mathExercise = signal<MathExercise>(undefined!);
    readonly course = signal<Course | undefined>(undefined);
    formattedProblemStatement: SafeHtml | null;
    formattedExampleSolution: SafeHtml | null;
    readonly submissions = signal<MathSubmission[]>([]);

    readonly doughnutStats = signal<ExerciseManagementStatisticsDto>(undefined!);
    readonly detailOverviewSections = signal<DetailOverviewSection[]>([]);

    private eventSubscriber: Subscription;

    ngOnInit() {
        this.route.data.subscribe(({ mathExercise }) => {
            this.mathExercise.set(mathExercise);
            this.onExerciseLoaded();
        });
        this.registerChangeInMathExercises();
    }

    onExerciseLoaded() {
        const exercise = this.mathExercise();
        this.course.set(exercise.course);

        this.formattedProblemStatement = this.artemisMarkdownService.safeHtmlForMarkdown(exercise.problemStatement);
        this.formattedExampleSolution = this.artemisMarkdownService.safeHtmlForMarkdown(exercise.exampleSolution);
        this.detailOverviewSections.set(this.getExerciseDetailSections());

        const exerciseId = exercise.id;
        if (exerciseId !== undefined) {
            this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
                this.doughnutStats.set(statistics);
            });

            this.mathSubmissionService.getSubmittedSubmissions(exerciseId).subscribe((submissions) => {
                this.submissions.set(submissions);
            });
        }
    }

    load(exerciseId: number) {
        this.mathExerciseService.find(exerciseId).subscribe((mathExerciseResponse: HttpResponse<MathExercise>) => {
            const exercise = mathExerciseResponse.body;
            if (exercise) {
                this.mathExercise.set(exercise);
                this.onExerciseLoaded();
            }
        });
    }

    getExerciseDetailSections(): DetailOverviewSection[] {
        const exercise = this.mathExercise();
        const generalSection = getExerciseGeneralDetailsSection(exercise);
        const modeSection = getExerciseModeDetailSection(exercise);
        const problemSection = getExerciseProblemDetailSection(this.formattedProblemStatement, exercise);
        const solutionSection = getExerciseMarkdownSolution(exercise, this.formattedExampleSolution);
        const defaultGradingDetails = getExerciseGradingDefaultDetails(exercise);

        return [
            generalSection,
            modeSection,
            problemSection,
            solutionSection,
            {
                headline: 'artemisApp.exercise.sections.grading',
                details: [...defaultGradingDetails],
            },
            {
                headline: 'artemisApp.mathExercise.description',
                details: [
                    {
                        type: DetailType.Markdown,
                        title: 'artemisApp.mathExercise.description',
                        data: { innerHtml: this.artemisMarkdownService.safeHtmlForMarkdown(exercise.description) },
                    },
                ],
            },
        ];
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInMathExercises() {
        this.eventSubscriber = this.eventManager.subscribe('mathExerciseListModification', () => {
            const exerciseId = this.mathExercise()?.id;
            if (exerciseId !== undefined) {
                this.load(exerciseId);
            }
        });
    }
}
