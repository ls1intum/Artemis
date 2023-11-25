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
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-text-exercise-detail',
    templateUrl: './text-exercise-detail.component.html',
})
export class TextExerciseDetailComponent implements OnInit, OnDestroy {
    readonly documentationType: DocumentationType = 'Text';

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
    detailOverviewSections: DetailOverviewSection[];

    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: EventManager,
        private textExerciseService: TextExerciseService,
        private exerciseService: ExerciseService,
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
            this.detailOverviewSections = this.getExerciseDetailSections();
        });
        this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
    }

    getExerciseDetailSections() {
        const exercise = this.textExercise;
        return [
            {
                headline: 'artemisApp.textExercise.sections.general',
                details: [
                    exercise.course && {
                        type: DetailType.Link,
                        title: 'artemisApp.exercise.course',
                        data: { text: exercise.course?.title, routerLink: ['/course-management', exercise.course?.id] },
                    },
                    exercise.exerciseGroup && {
                        type: DetailType.Link,
                        title: 'artemisApp.exercise.course',
                        data: { text: exercise.exerciseGroup?.exam?.course?.title, routerLink: ['/course-management', exercise.exerciseGroup.exam?.course?.id] },
                    },
                    exercise.exerciseGroup && {
                        type: DetailType.Link,
                        title: 'artemisApp.exercise.exam',
                        data: {
                            text: exercise.exerciseGroup.exam?.title,
                            routerLink: ['/course-management', exercise.exerciseGroup?.exam?.course?.id, 'exams', exercise.exerciseGroup?.exam?.id],
                        },
                    },
                    {
                        type: DetailType.Text,
                        title: 'artemisApp.exercise.title',
                        data: { text: exercise.title },
                    },
                    {
                        type: DetailType.Text,
                        title: 'artemisApp.exercise.categories',
                        data: { text: exercise.categories?.map((category) => category.category?.toUpperCase()).join(', ') },
                    },
                ].filter(Boolean),
            },
            {
                headline: 'artemisApp.textExercise.sections.mode',
                details: [
                    {
                        type: DetailType.Text,
                        title: 'artemisApp.exercise.difficulty',
                        data: { text: exercise.difficulty },
                    },
                    {
                        type: DetailType.Text,
                        title: 'artemisApp.exercise.mode',
                        data: { text: exercise.mode },
                    },
                    exercise.teamAssignmentConfig && {
                        type: DetailType.Text,
                        title: 'artemisApp.exercise.teamAssignmentConfig.teamSize',
                        data: { text: `Min. ${exercise.teamAssignmentConfig.minTeamSize}, Max. ${exercise.teamAssignmentConfig.maxTeamSize}` },
                    },
                ].filter(Boolean),
            },
            {
                headline: 'artemisApp.textExercise.sections.problem',
                details: [
                    {
                        type: DetailType.Markdown,
                        data: { innerHtml: this.formattedProblemStatement },
                    },
                ],
            },
            {
                headline: 'artemisApp.textExercise.sections.solution',
                details: [
                    {
                        type: DetailType.Markdown,
                        data: { innerHtml: this.formattedExampleSolution },
                    },
                ],
            },
            {
                headline: 'artemisApp.textExercise.sections.grading',
                details: [
                    { type: DetailType.Date, title: 'artemisApp.exercise.releaseDate', data: { date: exercise.releaseDate } },
                    { type: DetailType.Date, title: 'artemisApp.exercise.startDate', data: { date: exercise.startDate } },
                    { type: DetailType.Date, title: 'artemisApp.exercise.dueDate', data: { date: exercise.dueDate } },
                    { type: DetailType.Date, title: 'artemisApp.exercise.assessmentDueDate', data: { date: exercise.assessmentDueDate } },
                    { type: DetailType.Text, title: 'artemisApp.exercise.points', data: { text: exercise.maxPoints } },
                    exercise.bonusPoints && { type: DetailType.Text, title: 'artemisApp.exercise.bonusPoints', data: { text: exercise.bonusPoints } },
                    { type: DetailType.Text, title: 'artemisApp.exercise.includedInOverallScore', data: { text: this.exerciseService.isIncludedInScore(exercise) } },
                    { type: DetailType.Boolean, title: 'artemisApp.exercise.presentationScoreEnabled.title', data: { boolean: exercise.presentationScoreEnabled } },
                    { type: DetailType.Boolean, title: 'artemisApp.exercise.feedbackSuggestionsEnabled', data: { boolean: exercise.feedbackSuggestionsEnabled } },
                    exercise.gradingInstructions && {
                        type: DetailType.Markdown,
                        title: 'artemisApp.exercise.assessmentInstructions',
                        data: { innerHtml: this.formattedGradingInstructions },
                    },
                    exercise.gradingCriteria && {
                        type: DetailType.GradingCriteria,
                        title: 'artemisApp.exercise.structuredAssessmentInstructions',
                        data: { gradingCriteria: exercise.gradingCriteria },
                    },
                ].filter(Boolean),
            },
        ] as DetailOverviewSection[];
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
