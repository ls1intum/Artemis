import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { Subscription } from 'rxjs';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextExerciseService } from '../text-exercise/service/text-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import {
    getExerciseGeneralDetailsSection,
    getExerciseGradingDefaultDetails,
    getExerciseGradingInstructionsCriteriaDetails,
    getExerciseMarkdownSolution,
    getExerciseModeDetailSection,
    getExerciseProblemDetailSection,
} from 'app/exercise/util/utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';

@Component({
    selector: 'jhi-text-exercise-detail',
    templateUrl: './text-exercise-detail.component.html',
    imports: [TranslateDirective, DocumentationButtonComponent, NonProgrammingExerciseDetailCommonActionsComponent, ExerciseDetailStatisticsComponent, DetailOverviewListComponent],
})
export class TextExerciseDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private eventManager = inject(EventManager);
    private artemisMarkdownService = inject(ArtemisMarkdownService);
    private textExerciseService = inject(TextExerciseService);
    private statisticsService = inject(StatisticsService);

    readonly documentationType: DocumentationType = 'Text';

    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly dayjs = dayjs;

    textExercise: TextExercise;
    course?: Course;
    isExamExercise: boolean;
    formattedProblemStatement: SafeHtml | null;
    formattedExampleSolution: SafeHtml | null;
    formattedGradingInstructions: SafeHtml | null;

    doughnutStats: ExerciseManagementStatisticsDto;
    detailOverviewSections: DetailOverviewSection[];

    private subscription: Subscription;
    private eventSubscriber: Subscription;

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
        this.textExerciseService.find(exerciseId, false, true).subscribe((textExerciseResponse: HttpResponse<TextExercise>) => {
            this.textExercise = textExerciseResponse.body!;
            this.isExamExercise = !!this.textExercise.exerciseGroup;
            this.course = this.isExamExercise ? this.textExercise.exerciseGroup?.exam?.course : this.textExercise.course;

            this.formattedGradingInstructions = this.artemisMarkdownService.safeHtmlForMarkdown(this.textExercise.gradingInstructions);
            this.formattedProblemStatement = this.artemisMarkdownService.safeHtmlForMarkdown(this.textExercise.problemStatement);
            this.formattedExampleSolution = this.artemisMarkdownService.safeHtmlForMarkdown(this.textExercise.exampleSolution);
            this.detailOverviewSections = this.getExerciseDetailSections();
        });
        this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
    }

    getExerciseDetailSections(): DetailOverviewSection[] {
        const exercise = this.textExercise;
        const generalSection = getExerciseGeneralDetailsSection(exercise);
        const modeSection = getExerciseModeDetailSection(exercise);
        const problemSection = getExerciseProblemDetailSection(this.formattedProblemStatement, this.textExercise);
        const solutionSection = getExerciseMarkdownSolution(exercise, this.formattedExampleSolution);
        const defaultGradingDetails = getExerciseGradingDefaultDetails(exercise);
        const gradingInstructionsCriteriaDetails = getExerciseGradingInstructionsCriteriaDetails(exercise, this.formattedGradingInstructions);
        return [
            generalSection,
            modeSection,
            problemSection,
            solutionSection,
            {
                headline: 'artemisApp.exercise.sections.grading',
                details: [
                    ...defaultGradingDetails,
                    { type: DetailType.Boolean, title: 'artemisApp.exercise.allowFeedbackSuggestions', data: { boolean: !!exercise.athenaConfig?.feedbackSuggestionModule } },
                    { type: DetailType.Boolean, title: 'artemisApp.exercise.allowFeedbackRequests', data: { boolean: !!exercise.athenaConfig?.preliminaryFeedbackModule } },
                    ...gradingInstructionsCriteriaDetails,
                ],
            },
        ];
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
