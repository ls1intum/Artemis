import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { UMLModel, importDiagram } from '@tumaet/apollon';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { Subscription } from 'rxjs';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingExerciseService } from '../services/modeling-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import {
    getExerciseGeneralDetailsSection,
    getExerciseGradingDefaultDetails,
    getExerciseGradingInstructionsCriteriaDetails,
    getExerciseModeDetailSection,
    getExerciseProblemDetailSection,
} from 'app/exercise/util/utils';
import { DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';

@Component({
    selector: 'jhi-modeling-exercise-detail',
    templateUrl: './modeling-exercise-detail.component.html',
    imports: [TranslateDirective, DocumentationButtonComponent, NonProgrammingExerciseDetailCommonActionsComponent, ExerciseDetailStatisticsComponent, DetailOverviewListComponent],
})
export class ModelingExerciseDetailComponent implements OnInit, OnDestroy {
    private eventManager = inject(EventManager);
    private modelingExerciseService = inject(ModelingExerciseService);
    private route = inject(ActivatedRoute);
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private statisticsService = inject(StatisticsService);
    private profileService = inject(ProfileService);

    readonly documentationType: DocumentationType = 'Model';
    readonly ExerciseType = ExerciseType;
    readonly dayjs = dayjs;

    modelingExercise: ModelingExercise;
    course?: Course;
    private subscription: Subscription;
    private eventSubscriber: Subscription;
    problemStatement: SafeHtml;
    gradingInstructions: SafeHtml;
    exampleSolution: SafeHtml;
    exampleSolutionUML: UMLModel;
    detailOverviewSections: DetailOverviewSection[];

    doughnutStats: ExerciseManagementStatisticsDto;
    isExamExercise: boolean;

    isApollonProfileActive = false;

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            // Checks if the current environment includes "apollon" profile
            this.isApollonProfileActive = this.profileService.isProfileActive('apollon');
            this.load(params['exerciseId']);
        });
        this.registerChangeInModelingExercises();
    }

    load(exerciseId: number) {
        this.modelingExerciseService.find(exerciseId).subscribe((modelingExerciseResponse: HttpResponse<ModelingExercise>) => {
            this.modelingExercise = modelingExerciseResponse.body!;
            this.isExamExercise = this.modelingExercise.exerciseGroup !== undefined;
            this.course = this.isExamExercise ? this.modelingExercise.exerciseGroup?.exam?.course : this.modelingExercise.course;
            this.problemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.problemStatement);
            this.gradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.gradingInstructions);
            this.exampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.exampleSolutionExplanation);
            if (this.modelingExercise.exampleSolutionModel && this.modelingExercise.exampleSolutionModel !== '') {
                this.exampleSolutionUML = importDiagram(JSON.parse(this.modelingExercise.exampleSolutionModel));
            }
            this.detailOverviewSections = this.getExerciseDetailSections();
        });
        this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
    }

    getExerciseDetailSections(): DetailOverviewSection[] {
        const exercise = this.modelingExercise;
        const generalSection = getExerciseGeneralDetailsSection(exercise);
        const modeSection = getExerciseModeDetailSection(exercise);
        const problemSection = getExerciseProblemDetailSection(this.problemStatement, this.modelingExercise);
        const defaultGradingDetails = getExerciseGradingDefaultDetails(exercise);
        const gradingInstructionsCriteriaDetails = getExerciseGradingInstructionsCriteriaDetails(exercise, this.gradingInstructions);
        return [
            generalSection,
            modeSection,
            problemSection,
            {
                headline: 'artemisApp.exercise.sections.solution',
                details: [
                    {
                        type: DetailType.ModelingEditor,
                        title: 'artemisApp.exercise.sections.solution',
                        data: { umlModel: this.exampleSolutionUML, diagramType: exercise.diagramType, title: exercise.title, isApollonProfileActive: this.isApollonProfileActive },
                    },
                    {
                        title: 'artemisApp.modelingExercise.exampleSolutionExplanation',
                        type: DetailType.Markdown,
                        data: { innerHtml: this.exampleSolution },
                    },
                    {
                        title: 'artemisApp.exercise.exampleSolutionPublicationDate',
                        type: DetailType.Date,
                        data: { date: exercise.exampleSolutionPublicationDate },
                    },
                ],
            },
            {
                headline: 'artemisApp.exercise.sections.grading',
                details: [
                    ...defaultGradingDetails,
                    { type: DetailType.Text, title: 'artemisApp.modelingExercise.diagramType', data: { text: exercise.diagramType } },
                    ...gradingInstructionsCriteriaDetails,
                ],
            },
        ];
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInModelingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('modelingExerciseListModification', () => {
            this.load(this.modelingExercise.id!);
        });
    }
}
