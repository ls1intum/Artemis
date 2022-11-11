import { Component, OnDestroy, OnInit } from '@angular/core';
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

@Component({
    selector: 'jhi-modeling-exercise-detail',
    templateUrl: './modeling-exercise-detail.component.html',
})
export class ModelingExerciseDetailComponent implements OnInit, OnDestroy {
    documentationType = DocumentationType.Model;

    readonly dayjs = dayjs;
    modelingExercise: ModelingExercise;
    course: Course | undefined;
    private subscription: Subscription;
    private eventSubscriber: Subscription;
    problemStatement: SafeHtml;
    gradingInstructions: SafeHtml;
    exampleSolution: SafeHtml;
    exampleSolutionUML: UMLModel;
    numberOfClusters: number;

    readonly ExerciseType = ExerciseType;
    doughnutStats: ExerciseManagementStatisticsDto;
    isExamExercise: boolean;

    isAdmin = false;
    isApollonProfileActive = false;

    constructor(
        private eventManager: EventManager,
        private modelingExerciseService: ModelingExerciseService,
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private alertService: AlertService,
        private statisticsService: StatisticsService,
        private accountService: AccountService,
        private profileService: ProfileService,
    ) {}

    ngOnInit() {
        this.isAdmin = this.accountService.isAdmin();
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['exerciseId']);
        });
        this.registerChangeInModelingExercises();
        // Checks if the current environment includes "apollon" profile
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo && profileInfo.activeProfiles.includes('apollon')) {
                this.isApollonProfileActive = true;
            }
        });
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
        });
        this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
        if (this.isAdmin) {
            this.countModelClusters(exerciseId);
        }
    }

    downloadAsPDf() {
        const model = this.modelingExercise.exampleSolutionModel;
        if (model) {
            this.modelingExerciseService.convertToPdf(model, `${this.modelingExercise.title}-example-solution`).subscribe({
                error: () => {
                    this.alertService.error('artemisApp.modelingExercise.apollonConversion.error');
                },
            });
        }
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
                },
                error: () => {
                    this.alertService.error('artemisApp.modelingExercise.checkClusters.error');
                },
            });
        }
    }
}
