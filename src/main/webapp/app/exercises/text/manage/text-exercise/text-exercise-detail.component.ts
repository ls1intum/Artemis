import { Component, OnDestroy, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-text-exercise-detail',
    templateUrl: './text-exercise-detail.component.html',
})
export class TextExerciseDetailComponent implements OnInit, OnDestroy {
    documentationType = DocumentationType.Text;

    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly dayjs = dayjs;

    textExercise: TextExercise;
    course: Course | undefined;
    isExamExercise: boolean;
    formattedProblemStatement: SafeHtml | null;
    formattedExampleSolution: SafeHtml | null;
    formattedGradingInstructions: SafeHtml | null;

    doughnutStats: ExerciseManagementStatisticsDto;

    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: EventManager,
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
     * Requests the text exercise referenced by the given exerciseId.
     * @param exerciseId of the text exercise of type {number}
     */
    load(exerciseId: number) {
        // TODO: Use a separate find method for exam exercises containing course, exam, exerciseGroup and exercise exerciseId
        this.textExerciseService.find(exerciseId).subscribe((textExerciseResponse: HttpResponse<TextExercise>) => {
            this.textExercise = textExerciseResponse.body!;
            this.isExamExercise = !!this.textExercise.exerciseGroup;
            this.course = this.isExamExercise ? this.textExercise.exerciseGroup?.exam?.course : this.textExercise.course;

            this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.textExercise.gradingInstructions);
            this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.textExercise.problemStatement);
            this.formattedExampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.textExercise.exampleSolution);
        });
        this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
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
