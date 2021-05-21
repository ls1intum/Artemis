import { Component, OnDestroy, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { TextExercise } from 'app/entities/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { round } from 'app/shared/util/utils';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-text-exercise-detail',
    templateUrl: './text-exercise-detail.component.html',
})
export class TextExerciseDetailComponent implements OnInit, OnDestroy {
    readonly AssessmentType = AssessmentType;
    readonly DoughnutChartType = DoughnutChartType;
    readonly ExerciseType = ExerciseType;

    textExercise: TextExercise;
    courseId = 0;
    isExamExercise: boolean;
    formattedProblemStatement: SafeHtml | null;
    formattedSampleSolution: SafeHtml | null;
    formattedGradingInstructions: SafeHtml | null;

    doughnutStats: ExerciseManagementStatisticsDto;
    absoluteAveragePoints: number;
    participationsInPercent: number;
    questionsAnsweredInPercent: number;

    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private textExerciseService: TextExerciseService,
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private statisticsService: StatisticsService,
    ) {}

    /**
     * Loads the text exercise and subscribes to changes of it on component initialization.
     */
    ngOnInit() {
        // TODO: route determines whether the component is in exam mode
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['exerciseId']);
        });
        this.registerChangeInTextExercises();
    }

    /**
     * Requests the text exercise referenced by the given id.
     * @param id of the text exercise of type {number}
     */
    load(id: number) {
        // TODO: Use a separate find method for exam exercises containing course, exam, exerciseGroup and exercise id
        this.textExerciseService.find(id).subscribe((textExerciseResponse: HttpResponse<TextExercise>) => {
            this.textExercise = textExerciseResponse.body!;
            this.isExamExercise = !!this.textExercise.exerciseGroup;

            this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.textExercise.gradingInstructions);
            this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.textExercise.problemStatement);
            this.formattedSampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.textExercise.sampleSolution);
        });
        this.statisticsService.getExerciseStatistics(id).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
            this.participationsInPercent = statistics.numberOfStudentsInCourse > 0 ? round((statistics.numberOfParticipations / statistics.numberOfStudentsInCourse) * 100, 1) : 0;
            this.questionsAnsweredInPercent = statistics.numberOfQuestions > 0 ? round((statistics.numberOfAnsweredQuestions / statistics.numberOfQuestions) * 100, 1) : 0;
            this.absoluteAveragePoints = round((statistics.averageScoreOfExercise * statistics.maxPointsOfExercise) / 100, 1);
        });
    }

    /**
     * Unsubscribe from changes of text exercise on destruction of component.
     */
    ngOnDestroy() {
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * Subscribe to changes of the text exercise.
     */
    registerChangeInTextExercises() {
        this.eventSubscriber = this.eventManager.subscribe('textExerciseListModification', () => this.load(this.textExercise.id!));
    }
}
