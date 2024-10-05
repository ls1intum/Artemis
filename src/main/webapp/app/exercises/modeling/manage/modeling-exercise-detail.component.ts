import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { UMLModel } from '@ls1intum/apollon';
import { Subscription } from 'rxjs';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { ExerciseType } from 'app/entities/exercise.model';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { AlertService } from 'app/core/util/alert.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import {
    getExerciseGeneralDetailsSection,
    getExerciseGradingDefaultDetails,
    getExerciseGradingInstructionsCriteriaDetails,
    getExerciseModeDetailSection,
    getExerciseProblemDetailSection,
} from 'app/exercises/shared/utils';
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';

@Component({
    selector: 'jhi-modeling-exercise-detail',
    templateUrl: './modeling-exercise-detail.component.html',
})
export class ModelingExerciseDetailComponent implements OnInit, OnDestroy {
    private eventManager = inject(EventManager);
    private modelingExerciseService = inject(ModelingExerciseService);
    private route = inject(ActivatedRoute);
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private alertService = inject(AlertService);
    private statisticsService = inject(StatisticsService);
    private accountService = inject(AccountService);
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
    numberOfClusters: number;
    detailOverviewSections: DetailOverviewSection[];

    doughnutStats: ExerciseManagementStatisticsDto;
    isExamExercise: boolean;

    isAdmin = false;
    isApollonProfileActive = false;

    ngOnInit() {
        this.isAdmin = this.accountService.isAdmin();
        this.subscription = this.route.params.subscribe((params) => {
            // Checks if the current environment includes "apollon" profile
            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                if (profileInfo && profileInfo.activeProfiles.includes('apollon')) {
                    this.isApollonProfileActive = true;
                }
                this.load(params['exerciseId']);
            });
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
                this.exampleSolutionUML = JSON.parse(this.modelingExercise.exampleSolutionModel);
            }
            this.detailOverviewSections = this.getExerciseDetailSections();
        });
        this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
        if (this.isAdmin) {
            this.countModelClusters(exerciseId);
        }
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
                    this.isAdmin && { type: DetailType.Text, title: 'artemisApp.modelingExercise.checkClusters.text', data: { text: this.numberOfClusters } },
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

    buildModelClusters() {
        if (this.modelingExercise && this.modelingExercise.id) {
            this.modelingExerciseService.buildClusters(this.modelingExercise.id).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.modelingExercise.buildClusters.success');
                },
                error: () => {
                    this.alertService.error('artemisApp.modelingExercise.buildClusters.error');
                },
            });
        }
    }

    deleteModelClusters() {
        if (this.modelingExercise && this.modelingExercise.id) {
            this.modelingExerciseService.deleteClusters(this.modelingExercise.id).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.modelingExercise.deleteClusters.success');
                },
                error: () => {
                    this.alertService.error('artemisApp.modelingExercise.deleteClusters.error');
                },
            });
        }
    }

    countModelClusters(exerciseId: number) {
        if (exerciseId) {
            this.modelingExerciseService.getNumberOfClusters(exerciseId).subscribe({
                next: (res) => {
                    this.numberOfClusters = res?.body || 0;
                    this.detailOverviewSections = this.modelingExercise && this.getExerciseDetailSections();
                },
                error: () => {
                    this.alertService.error('artemisApp.modelingExercise.checkClusters.error');
                },
            });
        }
    }
}
