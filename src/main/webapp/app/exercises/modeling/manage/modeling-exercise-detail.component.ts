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
import dayjs from 'dayjs';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-modeling-exercise-detail',
    templateUrl: './modeling-exercise-detail.component.html',
})
export class ModelingExerciseDetailComponent implements OnInit, OnDestroy {
    readonly dayjs = dayjs;
    modelingExercise: ModelingExercise;
    course: Course | undefined;
    private subscription: Subscription;
    private eventSubscriber: Subscription;
    problemStatement: SafeHtml;
    gradingInstructions: SafeHtml;
    sampleSolution: SafeHtml;
    sampleSolutionUML: UMLModel;
    numberOfClusters: number;

    readonly ExerciseType = ExerciseType;
    doughnutStats: ExerciseManagementStatisticsDto;
    isExamExercise: boolean;

    isAdmin = false;

    constructor(
        private eventManager: EventManager,
        private modelingExerciseService: ModelingExerciseService,
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private alertService: AlertService,
        private statisticsService: StatisticsService,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        this.isAdmin = this.accountService.isAdmin();
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['exerciseId']);
        });
        this.registerChangeInModelingExercises();
    }

    load(id: number) {
        this.modelingExerciseService.find(id).subscribe((modelingExerciseResponse: HttpResponse<ModelingExercise>) => {
            this.modelingExercise = modelingExerciseResponse.body!;
            this.isExamExercise = this.modelingExercise.exerciseGroup !== undefined;
            this.course = this.isExamExercise ? this.modelingExercise.exerciseGroup?.exam?.course : this.modelingExercise.course;
            this.problemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.problemStatement);
            this.gradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.gradingInstructions);
            this.sampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.sampleSolutionExplanation);
            if (this.modelingExercise.sampleSolutionModel && this.modelingExercise.sampleSolutionModel !== '') {
                this.sampleSolutionUML = JSON.parse(this.modelingExercise.sampleSolutionModel);
            }
        });
        this.statisticsService.getExerciseStatistics(id).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
        if (this.isAdmin) {
            this.countModelClusters(id);
        }
    }

    downloadAsPDf() {
        const model = this.modelingExercise.sampleSolutionModel;
        if (model) {
            this.modelingExerciseService.convertToPdf(model, `${this.modelingExercise.title}-example-solution`).subscribe(
                () => {},
                () => {
                    this.alertService.error('artemisApp.modelingExercise.apollonConversion.error');
                },
            );
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
            this.modelingExerciseService.buildClusters(this.modelingExercise.id).subscribe(
                () => {
                    this.alertService.success('artemisApp.modelingExercise.buildClusters.success');
                },
                () => {
                    this.alertService.error('artemisApp.modelingExercise.buildClusters.error');
                },
            );
        }
    }

    deleteModelClusters() {
        if (this.modelingExercise && this.modelingExercise.id) {
            this.modelingExerciseService.deleteClusters(this.modelingExercise.id).subscribe(
                () => {
                    this.alertService.success('artemisApp.modelingExercise.deleteClusters.success');
                },
                () => {
                    this.alertService.error('artemisApp.modelingExercise.deleteClusters.error');
                },
            );
        }
    }

    countModelClusters(exerciseId: number) {
        if (exerciseId) {
            this.modelingExerciseService.getNumberOfClusters(exerciseId).subscribe(
                (res) => {
                    this.numberOfClusters = res?.body || 0;
                },
                () => {
                    this.alertService.error('artemisApp.modelingExercise.checkClusters.error');
                },
            );
        }
    }
}
