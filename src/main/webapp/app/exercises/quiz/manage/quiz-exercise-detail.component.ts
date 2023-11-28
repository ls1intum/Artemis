import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { getExerciseGeneralDetailsSection, getExerciseGradingDefaultDetails, getExerciseModeDetailSection } from 'app/exercises/shared/utils';
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { isQuizEditable } from 'app/exercises/quiz/shared/quiz-manage-util.service';
import { firstValueFrom } from 'rxjs';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-detail.component.html',
})
export class QuizExerciseDetailComponent implements OnInit {
    readonly documentationType: DocumentationType = 'Quiz';
    readonly dayjs = dayjs;

    courseId: number;
    examId: number;
    quizId: number;
    isExamMode: boolean;

    quizExercise: QuizExercise;
    statistics: ExerciseManagementStatisticsDto;

    detailOverviewSections: DetailOverviewSection[];

    constructor(
        private route: ActivatedRoute,
        private quizExerciseService: QuizExerciseService,
        private courseService: CourseManagementService,
        private statisticsService: StatisticsService,
    ) {}

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
            this.quizExercise.startDate = dayjs(this.quizExercise.dueDate).subtract(this.quizExercise.duration ?? 0, 'second');
            this.statistics = await firstValueFrom(this.statisticsService.getExerciseStatistics(this.quizId));
            console.log(this.quizExercise);
            this.detailOverviewSections = this.getExerciseDetailSections();
        });
    }

    getExerciseDetailSections() {
        const exercise = this.quizExercise;
        const generalSection = getExerciseGeneralDetailsSection(exercise);
        const modeSection = getExerciseModeDetailSection(exercise);
        const defaultGradingDetails = getExerciseGradingDefaultDetails(exercise);
        return [
            generalSection,
            {
                ...modeSection,
                details: [
                    ...modeSection.details,
                    { type: DetailType.Boolean, title: 'artemisApp.quizExercise.randomizeQuestionOrder', data: { boolean: exercise.randomizeQuestionOrder } },
                    { type: DetailType.Text, title: 'artemisApp.quizExercise.quizMode.title', data: { text: exercise.quizMode?.toUpperCase() } },
                ],
            },
            // problemSection,
            // solutionSection,
            {
                headline: 'artemisApp.exercise.sections.grading',
                details: [
                    ...defaultGradingDetails,
                    // { type: DetailType.Boolean, title: 'artemisApp.exercise.feedbackSuggestionsEnabled', data: { boolean: exercise.feedbackSuggestionsEnabled } },
                    // ...gradingInstructionsCriteriaDetails,
                ].filter(Boolean),
            },
        ] as DetailOverviewSection[];
    }
}
