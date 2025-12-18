import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import dayjs from 'dayjs/esm';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { getExerciseGeneralDetailsSection, getExerciseGradingDefaultDetails, getExerciseModeDetailSection } from 'app/exercise/util/utils';
import { DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { isQuizEditable } from 'app/quiz/shared/service/quiz-manage-util.service';
import { firstValueFrom } from 'rxjs';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { TranslateService } from '@ngx-translate/core';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { QuizExerciseManageButtonsComponent } from '../manage-buttons/quiz-exercise-manage-buttons.component';
import { QuizExerciseLifecycleButtonsComponent } from '../lifecyle-buttons/quiz-exercise-lifecycle-buttons.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-detail.component.html',
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        QuizExerciseManageButtonsComponent,
        QuizExerciseLifecycleButtonsComponent,
        ExerciseDetailStatisticsComponent,
        DetailOverviewListComponent,
    ],
})
export class QuizExerciseDetailComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private quizExerciseService = inject(QuizExerciseService);
    private statisticsService = inject(StatisticsService);
    private translateService = inject(TranslateService);

    readonly documentationType: DocumentationType = 'Quiz';
    readonly dayjs = dayjs;

    courseId: number;
    examId: number;
    quizId: number;
    isExamMode: boolean;
    showStatistics: boolean;

    quizExercise: QuizExercise;
    statistics: ExerciseManagementStatisticsDto;

    detailOverviewSections: DetailOverviewSection[];

    /**
     * Load the quizzes of the course for export on init.
     */
    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.quizId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const groupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        if (this.examId && groupId) {
            this.isExamMode = true;
        }
        this.load();
    }

    load() {
        this.quizExerciseService.find(this.quizId).subscribe(async (response: HttpResponse<QuizExercise>) => {
            this.quizExercise = response.body!;
            this.quizExercise.quizBatches = this.quizExercise.quizBatches?.sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
            this.quizExercise.isEditable = (this.quizExercise.isEditable ?? true) && isQuizEditable(this.quizExercise);
            this.quizExercise.status = this.quizExerciseService.getStatus(this.quizExercise);
            this.quizExercise.startDate = this.quizExercise.dueDate && dayjs(this.quizExercise.dueDate).subtract(this.quizExercise.duration ?? 0, 'second');
            this.showStatistics = !this.quizExercise.releaseDate || dayjs(this.quizExercise.releaseDate).isBefore(dayjs());
            if (this.showStatistics) {
                this.statistics = await firstValueFrom(this.statisticsService.getExerciseStatistics(this.quizId));
            }
            this.detailOverviewSections = this.getExerciseDetailSections();
        });
    }

    getExerciseDetailSections(): DetailOverviewSection[] {
        const exercise = this.quizExercise;
        const mcCount = this.quizExercise.quizQuestions?.filter((question) => question.type === QuizQuestionType.MULTIPLE_CHOICE)?.length ?? 0;
        const dndCount = this.quizExercise.quizQuestions?.filter((question) => question.type === QuizQuestionType.DRAG_AND_DROP)?.length ?? 0;
        const shortCount = this.quizExercise.quizQuestions?.filter((question) => question.type === QuizQuestionType.SHORT_ANSWER)?.length ?? 0;
        const generalSection = getExerciseGeneralDetailsSection(exercise);
        const modeSection = getExerciseModeDetailSection(exercise);
        const defaultGradingDetails = getExerciseGradingDefaultDetails(exercise);

        if (exercise.competencyLinks?.length) {
            modeSection.details.push({
                title: 'artemisApp.competency.link.title',
                type: DetailType.Text,
                data: { text: exercise.competencyLinks?.map((competencyLink) => competencyLink.competency?.title).join(', ') },
            });
        }
        return [
            generalSection,
            {
                ...modeSection,
                details: [
                    ...modeSection.details,
                    { type: DetailType.Boolean, title: 'artemisApp.quizExercise.randomizeQuestionOrder', data: { boolean: exercise.randomizeQuestionOrder } },
                    {
                        type: DetailType.Text,
                        title: 'artemisApp.quizExercise.quizMode.title',
                        data: { text: this.translateService.instant('artemisApp.quizExercise.quizMode.' + exercise.quizMode?.toLowerCase()).toUpperCase() },
                    },
                    {
                        type: DetailType.Text,
                        title: 'artemisApp.quizExercise.detail.questionCount.title',
                        data: { text: this.translateService.instant('artemisApp.quizExercise.detail.questionCount.value', { mcCount, dndCount, shortCount }) },
                    },
                ],
            },
            {
                headline: 'artemisApp.exercise.sections.grading',
                details: defaultGradingDetails,
            },
        ];
    }
}
