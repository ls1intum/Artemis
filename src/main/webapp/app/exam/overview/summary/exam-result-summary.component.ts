import { Component, Input, OnInit, inject } from '@angular/core';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exercise, ExerciseType, IncludedInOverallScore, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { ExerciseResult, StudentExamWithGradeDTO } from 'app/exam/shared/entities/exam-score-dtos.model';
import { ExamParticipationService } from 'app/exam/overview/exam-participation.service';
import { PlagiarismCasesService } from 'app/plagiarism/shared/plagiarism-cases.service';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { roundScorePercentSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { getLatestResultOfStudentParticipation } from 'app/exercise/participation/participation.utils';
import { evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercise/result/result.utils';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { faArrowUp, faEye, faEyeSlash, faFolderOpen, faInfoCircle, faPrint } from '@fortawesome/free-solid-svg-icons';
import { cloneDeep } from 'lodash-es';
import { captureException } from '@sentry/angular';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { isExamResultPublished } from 'app/exam/overview/exam.utils';
import { Course } from 'app/core/shared/entities/course.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExamGeneralInformationComponent } from '../general-information/exam-general-information.component';
import { ExamResultOverviewComponent } from './result-overview/exam-result-overview.component';
import { CollapsibleCardComponent } from './collapsible-card.component';
import { ExamResultSummaryExerciseCardHeaderComponent } from 'app/exam/overview/summary/exercises/header/exam-result-summary-exercise-card-header.component';
import { NgClass } from '@angular/common';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/programming-exercise-example-solution-repo-download.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TextExamSummaryComponent } from 'app/exam/overview/summary/exercises/text-exam-summary/text-exam-summary.component';
import { ModelingExamSummaryComponent } from 'app/exam/overview/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { QuizExamSummaryComponent } from 'app/exam/overview/summary/exercises/quiz-exam-summary/quiz-exam-summary.component';
import { FileUploadExamSummaryComponent } from 'app/exam/overview/summary/exercises/file-upload-exam-summary/file-upload-exam-summary.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { ProgrammingExamSummaryComponent } from 'app/exam/overview/summary/exercises/programming-exam-summary/programming-exam-summary.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExampleSolutionComponent } from 'app/exercise/example-solution/example-solution.component';

export type ResultSummaryExerciseInfo = {
    icon: IconProp;
    isCollapsed: boolean;
    achievedPoints?: number;
    achievedPercentage?: number;
    colorClass?: string;
    resultIconClass?: IconProp;

    submission?: Submission;
    participation?: Participation;
    displayExampleSolution: boolean;
    releaseTestsWithExampleSolution: boolean;
};

type StateBeforeResetting = {
    exerciseInfos: Record<number, ResultSummaryExerciseInfo>;
    isGradingKeyCollapsed: boolean;
    isBonusGradingKeyCollapsed: boolean;
};

@Component({
    selector: 'jhi-exam-participation-summary',
    templateUrl: './exam-result-summary.component.html',
    styleUrls: ['../../../core/course/manage/course-exercise-card.component.scss', '../../../quiz/shared/quiz.scss', 'exam-result-summary.component.scss'],
    imports: [
        FaIconComponent,
        TranslateDirective,
        ExamGeneralInformationComponent,
        ExamResultOverviewComponent,
        CollapsibleCardComponent,
        ExamResultSummaryExerciseCardHeaderComponent,
        NgClass,
        RouterLink,
        ProgrammingExerciseExampleSolutionRepoDownloadComponent,
        NgbTooltip,
        ExampleSolutionComponent,
        TextExamSummaryComponent,
        ModelingExamSummaryComponent,
        QuizExamSummaryComponent,
        FileUploadExamSummaryComponent,
        ComplaintsStudentViewComponent,
        ProgrammingExamSummaryComponent,
        ArtemisTranslatePipe,
    ],
})
export class ExamResultSummaryComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private serverDateService = inject(ArtemisServerDateService);
    private themeService = inject(ThemeService);
    private examParticipationService = inject(ExamParticipationService);
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private alertService = inject(AlertService);

    // make constants available to html for comparison
    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    readonly AssessmentType = AssessmentType;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly PlagiarismVerdict = PlagiarismVerdict;

    faFolderOpen = faFolderOpen;
    faInfoCircle = faInfoCircle;
    faPrint = faPrint;
    faEye = faEye;
    faEyeSlash = faEyeSlash;
    faArrowUp = faArrowUp;

    /**
     * Current student's exam.
     */
    private _studentExam: StudentExam;

    plagiarismCaseInfos: { [exerciseId: number]: PlagiarismCaseInfo } = {};
    exampleSolutionPublished = false;

    get studentExam(): StudentExam {
        return this._studentExam;
    }

    @Input()
    set studentExam(studentExam: StudentExam) {
        this._studentExam = studentExam;
        if (this.studentExamGradeInfoDTO) {
            this.studentExamGradeInfoDTO.studentExam = studentExam;
        }
        this.tryLoadPlagiarismCaseInfosForStudent();
    }

    /**
     * Grade info for current student's exam.
     */
    studentExamGradeInfoDTO: StudentExamWithGradeDTO;

    isGradingKeyCollapsed = true;
    isBonusGradingKeyCollapsed = true;

    @Input()
    instructorView = false;

    courseId: number;

    isTestRun = false;
    isTestExam = false;

    testRunConduction = false;
    testExamConduction = false;

    examWithOnlyIdAndStudentReviewPeriod: Exam;

    isBeforeStudentReviewEnd = false;
    /**
     * Used to decide whether to instantiate the ComplaintInteractionComponent. We always instantiate the component if
     * the review dates are set and the review start date has passed.
     */
    isAfterStudentReviewStart = false;

    exerciseInfos: Record<number, ResultSummaryExerciseInfo>;

    /**
     * Passed to components with overlapping elements to ensure that the overlapping
     * elements are displayed in a different order for printing.
     */
    isPrinting = false;

    /**
     * Passed to components where the problem statement might be expanded or collapsed to ensure that
     * the problem statement is expanded while printing
     */
    expandProblemStatement = false;

    /**
     * Initialise the courseId from the current url
     */
    ngOnInit(): void {
        // flags required to display test runs correctly
        this.isTestRun = this.route.snapshot.url[1]?.toString() === 'test-runs';
        this.isTestExam = this.studentExam.exam!.testExam!;
        this.testRunConduction = this.isTestRun && this.route.snapshot.url[3]?.toString() === 'conduction';
        this.testExamConduction = this.isTestExam && !this.studentExam.submitted;
        this.courseId = Number(this.route.snapshot?.paramMap?.get('courseId') || this.route.parent?.parent?.snapshot.paramMap.get('courseId'));
        if (!this.studentExam?.id) {
            throw new Error('studentExam.id should be present to fetch grade info');
        }
        if (!this.studentExam?.exam?.id) {
            throw new Error('studentExam.exam.id should be present to fetch grade info');
        }
        if (!this.studentExam?.user?.id) {
            throw new Error('studentExam.user.id should be present to fetch grade info');
        }

        if (isExamResultPublished(this.isTestRun, this.studentExam.exam, this.serverDateService)) {
            this.examParticipationService
                .loadStudentExamGradeInfoForSummary(this.courseId, this.studentExam.exam.id, this.studentExam.id, this.studentExam.user.id)
                .subscribe((studentExamWithGrade: StudentExamWithGradeDTO) => {
                    studentExamWithGrade.studentExam = this.studentExam;
                    this.studentExamGradeInfoDTO = studentExamWithGrade;
                    this.exerciseInfos = this.getExerciseInfos(studentExamWithGrade);
                });
        }

        this.exampleSolutionPublished = !!this.studentExam.exam?.exampleSolutionPublicationDate && dayjs().isAfter(this.studentExam.exam.exampleSolutionPublicationDate);

        this.exerciseInfos = this.getExerciseInfos();

        this.setExamWithOnlyIdAndStudentReviewPeriod();

        this.isBeforeStudentReviewEnd = this.getIsBeforeStudentReviewEnd();
        this.isAfterStudentReviewStart = this.getIsAfterStudentReviewStart();
    }

    get resultsArePublished(): boolean | any {
        if (this.isTestRun || this.isTestExam) {
            return true;
        }

        if (this.testRunConduction || this.testExamConduction) {
            return false;
        }

        if (this.studentExam?.exam?.publishResultsDate) {
            return dayjs(this.studentExam.exam.publishResultsDate).isBefore(dayjs());
        }

        return false;
    }

    private tryLoadPlagiarismCaseInfosForStudent() {
        // If the exam has not yet ended, or we're only a few minutes after the end, we can assume that there are no plagiarism cases yet.
        // We should avoid trying to load them to reduce server load.
        if (this.studentExam?.exam?.endDate) {
            const endDateWithTimeExtension = dayjs(this.studentExam.exam.endDate).add(2, 'hours');
            if (dayjs().isBefore(endDateWithTimeExtension)) {
                return;
            }
        }

        const exerciseIds = this.studentExam?.exercises?.map((exercise) => exercise.id!);
        if (exerciseIds?.length && this.courseId) {
            this.plagiarismCasesService.getPlagiarismCaseInfosForStudent(this.courseId, exerciseIds).subscribe((res) => {
                this.plagiarismCaseInfos = res.body ?? {};
            });
        }
    }

    /**
     * called for exportPDF Button
     */
    async printPDF() {
        const stateBeforeResetting = this.expandExercisesAndGradingKeysBeforePrinting();

        this.isPrinting = true;
        this.expandProblemStatement = true;

        await this.themeService.print();

        this.isPrinting = false;
        this.expandProblemStatement = false;

        this.resetExpandingExercisesAndGradingKeys(stateBeforeResetting);
    }

    scrollToOverviewOrTop() {
        const searchedId = 'exam-summary-result-overview';
        // go to result overview if it exists, otherwise go to top
        const targetElement = document.getElementById(searchedId) || document.getElementById('exam-results-title');

        if (targetElement) {
            targetElement.scrollIntoView({
                behavior: 'smooth',
                block: 'start',
                inline: 'nearest',
            });
        }
    }

    private expandExercisesAndGradingKeysBeforePrinting() {
        const stateBeforeResetting = {
            exerciseInfos: cloneDeep(this.exerciseInfos),
            isGradingKeyCollapsed: cloneDeep(this.isGradingKeyCollapsed),
            isBonusGradingKeyCollapsed: cloneDeep(this.isBonusGradingKeyCollapsed),
        };

        this.expandExercises();

        this.isGradingKeyCollapsed = false;
        this.isBonusGradingKeyCollapsed = false;

        return stateBeforeResetting;
    }

    private resetExpandingExercisesAndGradingKeys(stateBeforeResetting: StateBeforeResetting) {
        this.exerciseInfos = stateBeforeResetting.exerciseInfos;
        this.isGradingKeyCollapsed = stateBeforeResetting.isGradingKeyCollapsed;
        this.isBonusGradingKeyCollapsed = stateBeforeResetting.isBonusGradingKeyCollapsed;
    }

    private expandExercises() {
        Object.entries(this.exerciseInfos).forEach((exerciseInfo: [string, ResultSummaryExerciseInfo]) => {
            exerciseInfo[1].isCollapsed = false;
        });
    }

    public generateLink(exercise: Exercise) {
        if (exercise?.studentParticipations?.length) {
            return ['/courses', this.courseId, `${exercise.type}-exercises`, exercise.id, 'participate', exercise.studentParticipations[0].id];
        }
        return undefined;
    }

    /**
     * @param exercise
     * returns the students' submission for the exercise, undefined if no participation could be found
     */
    getSubmissionForExercise(exercise: Exercise) {
        return exercise?.studentParticipations?.[0]?.submissions?.[0];
    }

    /**
     * @param exercise
     * returns the students' submission for the exercise, undefined if no participation could be found
     */
    getParticipationForExercise(exercise: Exercise) {
        return exercise.studentParticipations?.[0] || undefined;
    }

    /**
     * We only need to pass these values to the ComplaintInteractionComponent
     */
    setExamWithOnlyIdAndStudentReviewPeriod() {
        const exam = new Exam();
        exam.id = this.studentExam?.exam?.id;
        exam.examStudentReviewStart = this.studentExam?.exam?.examStudentReviewStart;
        exam.examStudentReviewEnd = this.studentExam?.exam?.examStudentReviewEnd;
        exam.course = this.studentExam?.exam?.course;
        this.examWithOnlyIdAndStudentReviewPeriod = exam;
    }

    private getIsAfterStudentReviewStart() {
        if (this.isTestRun || this.isTestExam) {
            return true;
        }
        if (this.studentExam?.exam?.examStudentReviewStart && this.studentExam.exam.examStudentReviewEnd) {
            return this.serverDateService.now().isAfter(this.studentExam.exam.examStudentReviewStart);
        }
        return false;
    }

    private getIsBeforeStudentReviewEnd() {
        if (this.isTestRun || this.isTestExam) {
            return true;
        }
        if (this.studentExam?.exam?.examStudentReviewStart && this.studentExam.exam.examStudentReviewEnd) {
            return this.serverDateService.now().isBefore(this.studentExam.exam.examStudentReviewEnd);
        }
        return false;
    }

    private getExerciseInfos(studentExamWithGrade?: StudentExamWithGradeDTO): Record<number, ResultSummaryExerciseInfo> {
        const exerciseInfos: Record<number, ResultSummaryExerciseInfo> = {};
        for (const exercise of this.studentExam?.exercises ?? []) {
            if (exercise.id === undefined) {
                this.alertService.error('artemisApp.exam.error.cannotDisplayExerciseDetails', { exerciseGroupTitle: exercise.exerciseGroup?.title });
                const errorMessage = 'Cannot getExerciseInfos as exerciseId is undefined';
                captureException(new Error(errorMessage), {
                    extra: {
                        exercise,
                    },
                });
                continue;
            }

            const { textColorClass, resultIconClass } = this.getTextColorAndIconClassByExercise(exercise);

            exerciseInfos[exercise.id] = {
                icon: getIcon(exercise.type),
                isCollapsed: false,
                achievedPoints: this.getPointsByExerciseIdFromExam(exercise.id, studentExamWithGrade),
                achievedPercentage: this.getAchievedPercentageByExerciseId(exercise.id, studentExamWithGrade),
                colorClass: textColorClass,
                resultIconClass: resultIconClass,

                submission: this.getSubmissionForExercise(exercise),
                participation: this.getParticipationForExercise(exercise),
                displayExampleSolution: false,
                releaseTestsWithExampleSolution: exercise.type === ExerciseType.PROGRAMMING && !!(exercise as ProgrammingExercise).releaseTestsWithExampleSolution,
            };
        }
        return exerciseInfos;
    }

    private getPointsByExerciseIdFromExam(exerciseId: number, studentExamWithGrade?: StudentExamWithGradeDTO): number | undefined {
        if (!studentExamWithGrade) {
            return undefined;
        }

        for (const achievedPointsPerExerciseKey in this.studentExamGradeInfoDTO?.achievedPointsPerExercise) {
            if (Number(achievedPointsPerExerciseKey) === exerciseId) {
                return this.studentExamGradeInfoDTO.achievedPointsPerExercise[achievedPointsPerExerciseKey];
            }
        }

        return undefined;
    }

    private getExerciseResultByExerciseId(exerciseId?: number): ExerciseResult | undefined {
        if (exerciseId === undefined) {
            return undefined;
        }

        const exerciseGroupResultMapping = this.studentExamGradeInfoDTO?.studentResult?.exerciseGroupIdToExerciseResult;
        let exerciseResult = undefined;

        for (const key in exerciseGroupResultMapping) {
            if (key in exerciseGroupResultMapping && exerciseGroupResultMapping[key].exerciseId === exerciseId) {
                exerciseResult = exerciseGroupResultMapping[key];
                break;
            }
        }

        return exerciseResult;
    }

    toggleShowSampleSolution(exerciseId?: number) {
        if (exerciseId === undefined) {
            this.alertService.error('artemisApp.exam.error.cannotShowExampleSolution');
            const errorMessage = 'Cannot show sample solution because exercise id is undefined';
            captureException(new Error(errorMessage), {
                extra: {
                    exerciseId,
                },
            });

            return;
        }

        this.exerciseInfos[exerciseId].displayExampleSolution = !this.exerciseInfos[exerciseId].displayExampleSolution;
    }

    private calculateAchievedPercentageFromScoreAndMaxPoints(achievedPoints?: number, maxScore?: number, course?: Course) {
        const canCalculatePercentage = maxScore !== undefined && achievedPoints !== undefined;
        if (canCalculatePercentage) {
            return roundScorePercentSpecifiedByCourseSettings(achievedPoints! / maxScore, course);
        }

        return undefined;
    }

    private getAchievedPercentageFromResult(result: ExerciseResult, course?: Course) {
        if (result.achievedScore !== undefined) {
            return roundScorePercentSpecifiedByCourseSettings(result.achievedScore / 100, course);
        }

        return this.calculateAchievedPercentageFromScoreAndMaxPoints(result.achievedPoints, result.maxScore, course);
    }

    /**
     * This should only be needed when unsubmitted exercises are viewed, otherwise the results should be set
     */
    private getAchievedPercentageFromExamResults(exerciseId?: number, studentExamWithGrade?: StudentExamWithGradeDTO | undefined, course?: Course) {
        if (exerciseId === undefined) {
            return undefined;
        }

        const maxPoints = studentExamWithGrade?.studentExam?.exercises?.find((exercise) => exercise.id === exerciseId)?.maxPoints;
        const achievedPoints = this.getPointsByExerciseIdFromExam(exerciseId, studentExamWithGrade);

        return this.calculateAchievedPercentageFromScoreAndMaxPoints(achievedPoints, maxPoints, course);
    }

    getAchievedPercentageByExerciseId(exerciseId?: number, studentExamWithGrade?: StudentExamWithGradeDTO | undefined): number | undefined {
        const result = this.getExerciseResultByExerciseId(exerciseId);
        const course = this.studentExamGradeInfoDTO?.studentExam?.exam?.course;

        if (result === undefined) {
            return this.getAchievedPercentageFromExamResults(exerciseId, studentExamWithGrade, course);
        }

        return this.getAchievedPercentageFromResult(result, course);
    }

    getTextColorAndIconClassByExercise(exercise: Exercise) {
        const participation = exercise.studentParticipations![0];
        const showUngradedResults = false;
        const result = getLatestResultOfStudentParticipation(participation, showUngradedResults);

        const isBuilding = false;
        const templateStatus = evaluateTemplateStatus(exercise, participation, result, isBuilding);

        return {
            textColorClass: getTextColorClass(result, templateStatus),
            resultIconClass: getResultIconClass(result, templateStatus),
        };
    }

    toggleCollapseExercise(exerciseInfo: ResultSummaryExerciseInfo) {
        return () => (exerciseInfo!.isCollapsed = !exerciseInfo!.isCollapsed);
    }

    protected readonly getIcon = getIcon;
}
