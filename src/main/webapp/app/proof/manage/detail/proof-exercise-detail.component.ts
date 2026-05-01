import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { Subscription } from 'rxjs';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ProofExerciseService } from '../service/proof-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
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

@Component({
    selector: 'jhi-proof-exercise-detail',
    templateUrl: './proof-exercise-detail.component.html',
    imports: [TranslateDirective, DocumentationButtonComponent, NonProgrammingExerciseDetailCommonActionsComponent, ExerciseDetailStatisticsComponent, DetailOverviewListComponent],
})
export class ProofExerciseDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private eventManager = inject(EventManager);
    private artemisMarkdownService = inject(ArtemisMarkdownService);
    private proofExerciseService = inject(ProofExerciseService);
    private statisticsService = inject(StatisticsService);

    readonly ExerciseType = ExerciseType;

    proofExercise: ProofExercise;
    course?: Course;
    isExamExercise: boolean;
    formattedProblemStatement: SafeHtml | null;
    formattedExampleSolution: SafeHtml | null;

    doughnutStats: ExerciseManagementStatisticsDto;
    detailOverviewSections: DetailOverviewSection[];

    private subscription: Subscription;
    private eventSubscriber: Subscription;

    ngOnInit() {
        this.route.data.subscribe(({ proofExercise }) => {
            this.proofExercise = proofExercise;
            this.onExerciseLoaded();
        });
        this.registerChangeInProofExercises();
    }

    onExerciseLoaded() {
        this.isExamExercise = !!this.proofExercise.exerciseGroup;
        this.course = this.isExamExercise ? this.proofExercise.exerciseGroup?.exam?.course : this.proofExercise.course;

        this.formattedProblemStatement = this.artemisMarkdownService.safeHtmlForMarkdown(this.proofExercise.problemStatement);
        this.formattedExampleSolution = this.artemisMarkdownService.safeHtmlForMarkdown(this.proofExercise.exampleSolution);
        this.detailOverviewSections = this.getExerciseDetailSections();
        
        this.statisticsService.getExerciseStatistics(this.proofExercise.id!).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
    }

    load(exerciseId: number) {
        this.proofExerciseService.find(exerciseId).subscribe((proofExerciseResponse: HttpResponse<ProofExercise>) => {
            this.proofExercise = proofExerciseResponse.body!;
            this.onExerciseLoaded();
        });
    }

    getExerciseDetailSections(): DetailOverviewSection[] {
        const exercise = this.proofExercise;
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
                details: [
                    ...defaultGradingDetails,
                    { type: DetailType.Boolean, title: 'artemisApp.proofExercise.predefinedCheckboxState', data: { boolean: !!exercise.predefinedCheckboxState } },
                ],
            },
            {
                headline: 'artemisApp.proofExercise.description',
                details: [
                    { type: DetailType.Markdown, title: 'artemisApp.proofExercise.description', data: { innerHtml: this.artemisMarkdownService.safeHtmlForMarkdown(exercise.description) } },
                ],
            }
        ];
    }

    ngOnDestroy() {
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInProofExercises() {
        this.eventSubscriber = this.eventManager.subscribe('proofExerciseListModification', () => this.load(this.proofExercise.id!));
    }
}
