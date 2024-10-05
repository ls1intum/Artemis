import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { getExerciseGeneralDetailsSection, getExerciseGradingDefaultDetails, getExerciseModeDetailSection } from 'app/exercises/shared/utils';
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { isQuizEditable } from 'app/exercises/quiz/shared/quiz-manage-util.service';
import { firstValueFrom } from 'rxjs';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { TranslateService } from '@ngx-translate/core';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-detail.component.html',
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
            this.quizExercise.isEditable = isQuizEditable(this.quizExercise);
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

        if (exercise.competencies?.length) {
            modeSection.details.push({
                title: 'artemisApp.competency.link.title',
                type: DetailType.Text,
                data: { text: exercise.competencies?.map((competency) => competency.title).join(', ') },
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
