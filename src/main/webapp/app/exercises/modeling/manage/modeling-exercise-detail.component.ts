import { Component, OnDestroy, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { UMLModel } from '@ls1intum/apollon';
import { Subscription } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { round } from 'app/shared/util/utils';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';

@Component({
    selector: 'jhi-modeling-exercise-detail',
    templateUrl: './modeling-exercise-detail.component.html',
})
export class ModelingExerciseDetailComponent implements OnInit, OnDestroy {
    modelingExercise: ModelingExercise;
    private subscription: Subscription;
    private eventSubscriber: Subscription;
    problemStatement: SafeHtml;
    gradingInstructions: SafeHtml;
    sampleSolution: SafeHtml;
    sampleSolutionUML: UMLModel;

    readonly DoughnutChartType = DoughnutChartType;
    readonly ExerciseType = ExerciseType;
    doughnutStats: ExerciseManagementStatisticsDto;
    absoluteAveragePoints = 0;
    participationsInPercent = 0;
    questionsAnsweredInPercent = 0;

    constructor(
        private eventManager: JhiEventManager,
        private modelingExerciseService: ModelingExerciseService,
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private statisticsService: StatisticsService,
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['exerciseId']);
        });
        this.registerChangeInModelingExercises();
    }

    load(id: number) {
        this.modelingExerciseService.find(id).subscribe((modelingExerciseResponse: HttpResponse<ModelingExercise>) => {
            this.modelingExercise = modelingExerciseResponse.body!;
            this.problemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.problemStatement);
            this.gradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.gradingInstructions);
            this.sampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.sampleSolutionExplanation);
            if (this.modelingExercise.sampleSolutionModel && this.modelingExercise.sampleSolutionModel !== '') {
                this.sampleSolutionUML = JSON.parse(this.modelingExercise.sampleSolutionModel);
            }
        });
        this.statisticsService.getExerciseStatistics(id).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
            this.participationsInPercent = statistics.numberOfStudentsInCourse > 0 ? round((statistics.numberOfParticipations / statistics.numberOfStudentsInCourse) * 100, 1) : 0;
            this.questionsAnsweredInPercent = statistics.numberOfQuestions > 0 ? round((statistics.numberOfAnsweredQuestions / statistics.numberOfQuestions) * 100, 1) : 0;
            this.absoluteAveragePoints = round((statistics.averageScoreOfExercise * statistics.maxPointsOfExercise) / 100, 1);
        });
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInModelingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('modelingExerciseListModification', () => this.load(this.modelingExercise.id!));
    }
}
