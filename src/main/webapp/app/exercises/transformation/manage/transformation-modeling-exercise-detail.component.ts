import { Component, OnDestroy, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { UMLModel } from '@ls1intum/apollon';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { TransformationModelingExercise } from 'app/entities/transformation-modeling-exercise.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { ExerciseType } from 'app/entities/exercise.model';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import * as moment from 'moment';

@Component({
    selector: 'jhi-transformation-modeling-exercise-detail',
    templateUrl: './transformation-modeling-exercise-detail.component.html',
})
export class TransformationModelingExerciseDetailComponent implements OnInit, OnDestroy {
    transformationModelingExercise: TransformationModelingExercise;
    private subscription: Subscription;
    private eventSubscriber: Subscription;
    problemStatement: SafeHtml;
    gradingInstructions: SafeHtml;
    sampleSolution: SafeHtml;
    sampleSolutionUML: UMLModel;

    readonly ExerciseType = ExerciseType;
    readonly moment = moment;
    doughnutStats: ExerciseManagementStatisticsDto;

    constructor(
        private eventManager: JhiEventManager,
        private modelingExerciseService: ModelingExerciseService,
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private jhiAlertService: JhiAlertService,
        private statisticsService: StatisticsService,
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['exerciseId']);
        });
        this.registerChangeInModelingExercises();
    }

    load(id: number) {
        this.modelingExerciseService.find(id).subscribe((modelingExerciseResponse: HttpResponse<TransformationModelingExercise>) => {
            this.transformationModelingExercise = modelingExerciseResponse.body!;
            this.problemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.transformationModelingExercise.problemStatement);
            this.gradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.transformationModelingExercise.gradingInstructions);
            this.sampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.transformationModelingExercise.sampleSolutionExplanation);
            if (this.transformationModelingExercise.sampleSolutionModel && this.transformationModelingExercise.sampleSolutionModel !== '') {
                this.sampleSolutionUML = JSON.parse(this.transformationModelingExercise.sampleSolutionModel);
            }
        });
        this.statisticsService.getExerciseStatistics(id).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
    }

    downloadAsPDf() {
        const model = this.transformationModelingExercise.sampleSolutionModel;
        if (model) {
            this.modelingExerciseService.convertToPdf(model, `${this.transformationModelingExercise.title}-example-solution`).subscribe(
                () => {},
                () => {
                    this.jhiAlertService.error('artemisApp.modelingExercise.apollonConversion.error');
                },
            );
        }
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInModelingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('modelingExerciseListModification', () => this.load(this.transformationModelingExercise.id!));
    }
}
