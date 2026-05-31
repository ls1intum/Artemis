import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { Subscription } from 'rxjs';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { MathExerciseService } from '../service/math-exercise.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import {
    getExerciseGeneralDetailsSection,
    getExerciseGradingDefaultDetails,
    getExerciseMarkdownSolution,
    getExerciseModeDetailSection,
    getExerciseProblemDetailSection,
} from 'app/exercise/util/utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
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
        MathNodeLatexPipe,
        RouterLink,
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

    mathExercise: MathExercise;
    course?: Course;
    formattedProblemStatement: SafeHtml | null;
    formattedExampleSolution: SafeHtml | null;
    submissions: MathSubmission[] = [];

    doughnutStats: ExerciseManagementStatisticsDto;
    detailOverviewSections: DetailOverviewSection[];

    private subscription: Subscription;
    private eventSubscriber: Subscription;

    ngOnInit() {
        this.route.data.subscribe(({ mathExercise }) => {
            this.mathExercise = mathExercise;
            this.onExerciseLoaded();
        });
        this.registerChangeInMathExercises();
    }

    onExerciseLoaded() {
        this.course = this.mathExercise.course;

        this.formattedProblemStatement = this.artemisMarkdownService.safeHtmlForMarkdown(this.mathExercise.problemStatement);
        this.formattedExampleSolution = this.artemisMarkdownService.safeHtmlForMarkdown(this.mathExercise.exampleSolution);
        this.detailOverviewSections = this.getExerciseDetailSections();

        this.statisticsService.getExerciseStatistics(this.mathExercise.id!).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });

        this.mathSubmissionService.getSubmittedSubmissions(this.mathExercise.id!).subscribe((submissions) => {
            this.submissions = submissions;
        });
    }

    load(exerciseId: number) {
        this.mathExerciseService.find(exerciseId).subscribe((mathExerciseResponse: HttpResponse<MathExercise>) => {
            this.mathExercise = mathExerciseResponse.body!;
            this.onExerciseLoaded();
        });
    }

    getExerciseDetailSections(): DetailOverviewSection[] {
        const exercise = this.mathExercise;
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
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInMathExercises() {
        this.eventSubscriber = this.eventManager.subscribe('mathExerciseListModification', () => this.load(this.mathExercise.id!));
    }
}
