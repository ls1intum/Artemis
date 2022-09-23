import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { SortService } from 'app/shared/service/sort.service';
import { ExportToCsv } from 'export-to-csv';
import {
    AggregatedExamResult,
    AggregatedExerciseGroupResult,
    AggregatedExerciseResult,
    ExamScoreDTO,
    ExerciseGroup,
    StudentResult,
    TableState,
} from 'app/exam/exam-scores/exam-score-dtos.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { TranslateService } from '@ngx-translate/core';
import { ParticipantScoresService, ScoresDTO } from 'app/shared/participant-scores/participant-scores.service';
import { captureException } from '@sentry/browser';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { declareExerciseType } from 'app/entities/exercise.model';
import { mean, median, standardDeviation } from 'simple-statistics';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { faCheckCircle, faDownload, faSort, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { CsvExportRowBuilder } from 'app/shared/export/csv-export-row-builder';
import { ExcelExportRowBuilder } from 'app/shared/export/excel-export-row-builder';
import { CsvExportOptions } from 'app/shared/export/export-modal.component';
import { ExportRow, ExportRowBuilder } from 'app/shared/export/export-row-builder';
import * as XLSX from 'xlsx';
import { VERSION } from 'app/app.constants';
import {
    BONUS_KEY,
    EMAIL_KEY,
    EXAM_ACHIEVED_POINTS,
    EXAM_ACHIEVED_SCORE,
    EXAM_ASSIGNED_EXERCISE,
    EXAM_PASSED,
    EXAM_SUBMITTED,
    GRADE_KEY,
    NAME_KEY,
    EXAM_OVERALL_POINTS_KEY,
    EXAM_OVERALL_SCORE_KEY,
    REGISTRATION_NUMBER_KEY,
    USERNAME_KEY,
    BONUS_GRADE_KEY,
    FINAL_GRADE_KEY,
    PLAGIARISM_VERDICT_KEY,
    PLAGIARISM_VERDICT_IN_BONUS_SOURCE_KEY,
} from 'app/shared/export/export-constants';

export enum MedianType {
    PASSED,
    OVERALL,
    SUBMITTED,
}

@Component({
    selector: 'jhi-exam-scores',
    templateUrl: './exam-scores.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrls: ['./exam-scores.component.scss', '../../shared/chart/vertical-bar-chart.scss'],
})
export class ExamScoresComponent implements OnInit, OnDestroy {
    public examScoreDTO: ExamScoreDTO;
    public exerciseGroups: ExerciseGroup[];
    public studentResults: StudentResult[];

    // Data structures for calculated statistics
    // TODO: Cache already calculated filter dependent statistics
    public aggregatedExamResults: AggregatedExamResult;
    public aggregatedExerciseGroupResults: AggregatedExerciseGroupResult[];
    public noOfExamsFiltered: number;

    dataLabelFormatting = this.formatDataLabel.bind(this);
    scores: number[];
    gradesWithBonus: string[];
    lastCalculatedMedianType: MedianType;
    highlightedValue: number | undefined;

    showOverallMedian: boolean; // Indicates whether the median of all exams is currently highlighted
    overallChartMedian: number; // This value can vary as it depends on if the user only includes submitted exams or not
    overallChartMedianType: MedianType; // We need to distinguish the different overall medians for the toggling
    showPassedMedian: boolean; // Same as above for the median of all passed exams

    // table entries
    tableState: TableState = new TableState();

    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly medianType = MedianType;
    readonly ButtonSize = ButtonSize;

    // exam score dtos
    studentIdToExamScoreDTOs: Map<number, ScoresDTO> = new Map<number, ScoresDTO>();

    public predicate = 'id';
    public reverse = false;
    public isLoading = true;
    public filterForSubmittedExams = false;
    public filterForNonEmptySubmissions = false;

    gradingScaleExists = false;
    gradingScale?: GradingScale;
    isBonus?: boolean;
    hasBonus?: boolean;
    hasPlagiarismVerdicts?: boolean;
    hasPlagiarismVerdictsInBonusSource?: boolean;
    hasSecondCorrectionAndStarted: boolean;
    hasNumericGrades: boolean;

    course?: Course;

    // Icons
    faSort = faSort;
    faDownload = faDownload;
    faTimes = faTimes;
    faCheckCircle = faCheckCircle;

    private languageChangeSubscription?: Subscription;
    constructor(
        private route: ActivatedRoute,
        private examService: ExamManagementService,
        private sortService: SortService,
        private alertService: AlertService,
        private changeDetector: ChangeDetectorRef,
        private languageHelper: JhiLanguageHelper,
        private localeConversionService: LocaleConversionService,
        private translateService: TranslateService,
        private participantScoresService: ParticipantScoresService,
        private gradingSystemService: GradingSystemService,
        private courseManagementService: CourseManagementService,
        private navigationUtilService: ArtemisNavigationUtilService,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            const getExamScoresObservable = this.examService.getExamScores(params['courseId'], params['examId']);
            // alternative exam scores calculation using participant scores table
            const findExamScoresObservable = this.participantScoresService.findExamScores(params['examId']);

            // find grading scale if one exists and handle case when it doesn't
            const gradingScaleObservable = this.gradingSystemService
                .findGradingScaleForExam(params['courseId'], params['examId'])
                .pipe(catchError(() => of(new HttpResponse<GradingScale>())));

            this.courseManagementService.find(params['courseId']).subscribe((courseResponse) => (this.course = courseResponse.body!));

            forkJoin([getExamScoresObservable, findExamScoresObservable, gradingScaleObservable]).subscribe({
                next: ([getExamScoresResponse, findExamScoresResponse, gradingScaleResponse]) => {
                    this.examScoreDTO = getExamScoresResponse!.body!;
                    if (this.examScoreDTO) {
                        this.hasSecondCorrectionAndStarted = this.examScoreDTO.hasSecondCorrectionAndStarted;
                        this.studentResults = this.examScoreDTO.studentResults;
                        this.exerciseGroups = this.examScoreDTO.exerciseGroups;

                        const titleMap = new Map<string, number>();
                        if (this.exerciseGroups) {
                            for (const exerciseGroup of this.exerciseGroups) {
                                if (titleMap.has(exerciseGroup.title)) {
                                    const currentValue = titleMap.get(exerciseGroup.title);
                                    titleMap.set(exerciseGroup.title, currentValue! + 1);
                                } else {
                                    titleMap.set(exerciseGroup.title, 1);
                                }
                            }

                            // this workaround is necessary if the exam has exercise groups with the same title (we add the id to make it unique)
                            for (const exerciseGroup of this.exerciseGroups) {
                                if (titleMap.has(exerciseGroup.title) && titleMap.get(exerciseGroup.title)! > 1) {
                                    exerciseGroup.title = `${exerciseGroup.title} (id=${exerciseGroup.id})`;
                                }
                            }
                        }
                    }
                    // set the grading scale if it exists for the exam
                    if (gradingScaleResponse.body) {
                        this.gradingScaleExists = true;
                        this.gradingScale = gradingScaleResponse.body!;
                        this.isBonus = this.gradingScale!.gradeType === GradeType.BONUS;
                        this.hasBonus = !!this.studentResults?.some((studentResult) => studentResult?.gradeWithBonus);
                        this.gradingScale!.gradeSteps = this.gradingSystemService.sortGradeSteps(this.gradingScale!.gradeSteps);
                        this.hasNumericGrades = !this.gradingScale!.gradeSteps.some((step) => isNaN(Number(step.gradeName)));
                    }
                    // Only try to calculate statistics if the exam has exercise groups and student results
                    if (this.studentResults && this.exerciseGroups) {
                        this.hasPlagiarismVerdicts = this.studentResults.some((studentResult) => studentResult.mostSeverePlagiarismVerdict);
                        this.hasPlagiarismVerdictsInBonusSource =
                            this.hasBonus && this.studentResults.some((studentResult) => studentResult.gradeWithBonus?.mostSeverePlagiarismVerdict);

                        // Exam statistics must only be calculated once as they are not filter dependent
                        this.calculateExamStatistics();
                        this.calculateFilterDependentStatistics();
                        const medianType = this.gradingScaleExists && !this.isBonus ? MedianType.PASSED : MedianType.OVERALL;
                        // if a grading scale exists and the scoring type is not bonus, per default the median of all passed exams is shown.
                        // We need to set the value for the overall median in order to show it next to the checkbox
                        if (medianType === MedianType.PASSED) {
                            // We pass MedianType.OVERALL since we want the median of all exams to be shown, not only of the submitted exams
                            this.setOverallChartMedianDependingOfExamsIncluded(MedianType.OVERALL);
                            this.showOverallMedian = false;
                        }
                        this.determineAndHighlightChartMedian(medianType);
                    }
                    this.isLoading = false;
                    this.changeDetector.detectChanges();
                    this.compareNewExamScoresCalculationWithOldCalculation(findExamScoresResponse.body!);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        });

        // Update the view if the language was changed
        this.languageChangeSubscription = this.languageHelper.language.subscribe(() => {
            this.changeDetector.detectChanges();
        });
    }

    ngOnDestroy() {
        if (this.languageChangeSubscription) {
            this.languageChangeSubscription.unsubscribe();
        }
    }

    toggleFilterForSubmittedExam() {
        this.filterForSubmittedExams = !this.filterForSubmittedExams;
        this.calculateFilterDependentStatistics();
        const overallMedianType = this.filterForSubmittedExams ? MedianType.SUBMITTED : MedianType.OVERALL;
        /*
        if a grading scale exists that is not configured as bonus, we have to update the
        overall median value as we only encounter submitted exams now.
        For the median of all passed exams this is not necessary, as an exam can only pass
        if it is submitted.
         */
        if (this.gradingScaleExists && !this.isBonus) {
            this.setOverallChartMedianDependingOfExamsIncluded(overallMedianType);
            this.showOverallMedian = false;
            this.showPassedMedian = true;
            this.determineAndHighlightChartMedian(MedianType.PASSED);
        } else {
            this.showOverallMedian = true;
            this.determineAndHighlightChartMedian(overallMedianType);
        }
        this.updateValuesAccordingToFilter();
        this.changeDetector.detectChanges();
    }

    toggleFilterForNonEmptySubmission() {
        this.filterForNonEmptySubmissions = !this.filterForNonEmptySubmissions;
        this.calculateFilterDependentStatistics();
        this.updateValuesAccordingToFilter();
        this.changeDetector.detectChanges();
    }

    /**
     * Calculate statistics on exercise group and exercise granularity. These statistics are filter dependent.
     * @param exerciseGroupResults Data structure holding the aggregated points and number of participants
     */
    private calculateExerciseGroupStatistics(exerciseGroupResults: AggregatedExerciseGroupResult[]) {
        for (const groupResult of exerciseGroupResults) {
            // For average points for exercise groups
            if (groupResult.noOfParticipantsWithFilter) {
                groupResult.averagePoints = groupResult.totalPoints / groupResult.noOfParticipantsWithFilter;
                groupResult.averagePercentage = (groupResult.averagePoints / groupResult.maxPoints) * 100;
                if (this.gradingScaleExists) {
                    const gradeStep = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, groupResult.averagePercentage);
                    groupResult.averageGrade = gradeStep!.gradeName;
                }
            }
            // Calculate average points for exercises
            groupResult.exerciseResults.forEach((exResult: AggregatedExerciseResult) => {
                if (exResult.noOfParticipantsWithFilter) {
                    exResult.averagePoints = exResult.totalPoints / exResult.noOfParticipantsWithFilter;
                    exResult.averagePercentage = (exResult.averagePoints / exResult.maxPoints) * 100;
                }
            });
        }
        this.aggregatedExerciseGroupResults = exerciseGroupResults;
    }

    /**
     * Calculates filter dependent exam statistics. Must be triggered if filter settings change.
     * 1. The average points and number of participants for each exercise group and exercise
     * 2. Distribution of scores
     */
    private calculateFilterDependentStatistics() {
        this.noOfExamsFiltered = 0;
        const scoresToVisualize: number[] = [];
        const gradesWithBonusToVisualize: string[] = [];

        // Create data structures holding the statistics for all exercise groups and exercises
        const groupIdToGroupResults = new Map<number, AggregatedExerciseGroupResult>();
        for (const exerciseGroup of this.exerciseGroups) {
            const groupResult = new AggregatedExerciseGroupResult(exerciseGroup.id, exerciseGroup.title, exerciseGroup.maxPoints, exerciseGroup.numberOfParticipants);
            // We initialize the data structure for exercises here as it can happen that no student was assigned to an exercise
            exerciseGroup.containedExercises.forEach((exerciseInfo) => {
                const type = declareExerciseType(exerciseInfo);
                const exerciseResult = new AggregatedExerciseResult(exerciseInfo.exerciseId, exerciseInfo.title, exerciseInfo.maxPoints, exerciseInfo.numberOfParticipants, type!);
                groupResult.exerciseResults.push(exerciseResult);
            });
            groupIdToGroupResults.set(exerciseGroup.id, groupResult);
        }

        // Calculate the total points and number of participants when filters apply for each exercise group and exercise
        for (const studentResult of this.studentResults) {
            // Do not take un-submitted exams into account for the exercise statistics if the option was set
            if (!studentResult.submitted && this.filterForSubmittedExams) {
                continue;
            }
            scoresToVisualize.push(studentResult.overallScoreAchieved ?? 0);
            if (this.hasBonus) {
                gradesWithBonusToVisualize.push(studentResult.gradeWithBonus?.finalGrade?.toString() ?? '');
            }
            this.noOfExamsFiltered++;
            if (!studentResult.exerciseGroupIdToExerciseResult) {
                continue;
            }
            const entries = Object.entries(studentResult.exerciseGroupIdToExerciseResult);

            for (const [exGroupId, studentExerciseResult] of entries) {
                // Ignore exercise results with only empty submission if the option was set
                if (!studentExerciseResult.hasNonEmptySubmission && this.filterForNonEmptySubmissions) {
                    continue;
                }
                // Update the exerciseGroup statistic
                const exGroupResult = groupIdToGroupResults.get(Number(exGroupId));
                if (!exGroupResult) {
                    // This should never be thrown. Indicates that the information in the ExamScoresDTO is inconsistent
                    throw new Error(`ExerciseGroup with id ${exGroupId} does not exist in this exam!`);
                }
                exGroupResult.noOfParticipantsWithFilter++;
                exGroupResult.totalPoints += studentExerciseResult.achievedPoints!;

                // Update the specific exercise statistic
                const exerciseResult = exGroupResult.exerciseResults.find((exResult) => exResult.exerciseId === studentExerciseResult.exerciseId);
                if (!exerciseResult) {
                    // This should never be thrown. Indicates that the information in the ExamScoresDTO is inconsistent
                    throw new Error(`Exercise with id ${studentExerciseResult.exerciseId} does not exist in this exam!`);
                } else {
                    exerciseResult.noOfParticipantsWithFilter++;
                    exerciseResult.totalPoints += studentExerciseResult.achievedPoints!;
                }
            }
        }
        // Calculate exercise group and exercise statistics
        const exerciseGroupResults = Array.from(groupIdToGroupResults.values());
        this.calculateExerciseGroupStatistics(exerciseGroupResults);
        this.scores = [...scoresToVisualize];
        this.gradesWithBonus = gradesWithBonusToVisualize;
    }

    /**
     * Calculates statistics on exam granularity for passed exams, submitted exams, and for all exams.
     */
    private calculateExamStatistics() {
        let numberNonEmptySubmissions = 0;
        let numberNonEmptySubmittedSubmissions = 0;

        const studentPointsPassed: number[] = [];
        const studentPointsSubmitted: number[] = [];
        const studentPointsTotal: number[] = [];

        const studentPointsPassedInFirstCorrectionRound: number[] = [];
        const studentPointsSubmittedInFirstCorrectionRound: number[] = [];
        const studentPointsTotalInFirstCorrectionRound: number[] = [];

        const studentGradesPassed: number[] = [];
        const studentGradesSubmitted: number[] = [];
        const studentGradesTotal: number[] = [];

        const studentGradesPassedInFirstCorrectionRound: number[] = [];
        const studentGradesSubmittedInFirstCorrectionRound: number[] = [];
        const studentGradesTotalInFirstCorrectionRound: number[] = [];

        // Collect student points independent of the filter settings
        for (const studentResult of this.studentResults) {
            studentPointsTotal.push(studentResult.overallPointsAchieved!);
            studentPointsTotalInFirstCorrectionRound.push(studentResult.overallPointsAchievedInFirstCorrection!);
            if (studentResult.submitted) {
                studentPointsSubmitted.push(studentResult.overallPointsAchieved!);
                studentPointsSubmittedInFirstCorrectionRound.push(studentResult.overallPointsAchievedInFirstCorrection!);
                if (studentResult.hasPassed) {
                    studentPointsPassed.push(studentResult.overallPointsAchieved!);
                    studentPointsPassedInFirstCorrectionRound.push(studentResult.overallPointsAchievedInFirstCorrection!);
                }
            }
            if (studentResult.exerciseGroupIdToExerciseResult) {
                const entries = Object.entries(studentResult.exerciseGroupIdToExerciseResult);
                if (entries.some(([, exerciseResult]) => exerciseResult.hasNonEmptySubmission)) {
                    numberNonEmptySubmissions += 1;
                    if (studentResult.submitted) {
                        numberNonEmptySubmittedSubmissions += 1;
                    }
                }
            }

            if (this.gradingScaleExists && this.hasNumericGrades) {
                const grade = Number(studentResult.overallGrade);
                const gradeInFirstCorrection = Number(studentResult.overallGradeInFirstCorrection);
                studentGradesTotal.push(grade);
                studentGradesTotalInFirstCorrectionRound.push(gradeInFirstCorrection);
                if (studentResult.submitted) {
                    studentGradesSubmitted.push(grade);
                    studentGradesSubmittedInFirstCorrectionRound.push(gradeInFirstCorrection);
                    if (studentResult.hasPassed) {
                        studentGradesPassed.push(grade);
                        studentGradesPassedInFirstCorrectionRound.push(gradeInFirstCorrection);
                    }
                }
            }
        }
        // Calculate statistics for passed exams
        let examStatistics = this.calculatePassedExamStatistics(
            new AggregatedExamResult(),
            studentPointsPassed,
            studentPointsPassedInFirstCorrectionRound,
            studentGradesPassed,
            studentGradesPassedInFirstCorrectionRound,
        );
        // Calculate statistics for submitted exams
        examStatistics = this.calculateSubmittedExamStatistics(
            examStatistics,
            studentPointsSubmitted,
            studentPointsSubmittedInFirstCorrectionRound,
            studentGradesSubmitted,
            studentGradesSubmittedInFirstCorrectionRound,
        );
        // Calculate total statistics
        this.aggregatedExamResults = this.calculateTotalExamStatistics(
            examStatistics,
            studentPointsTotal,
            studentPointsTotalInFirstCorrectionRound,
            studentGradesTotal,
            studentGradesTotalInFirstCorrectionRound,
        );
        this.aggregatedExamResults.noOfExamsNonEmpty = numberNonEmptySubmissions;
        this.aggregatedExamResults.noOfExamsSubmittedAndNotEmpty = numberNonEmptySubmittedSubmissions;
        this.updateValuesAccordingToFilter();
        this.changeDetector.detectChanges();
    }

    /**
     * Calculates statistics like mean, median and standard deviation specifically for passed exams
     */
    private calculatePassedExamStatistics(
        examStatistics: AggregatedExamResult,
        studentPointsPassed: number[],
        studentPointsPassedInFirstCorrectionRound: number[],
        studentGradesPassed: number[],
        studentGradesPassedInFirstCorrectionRound: number[],
    ): AggregatedExamResult {
        if (studentPointsPassed.length && this.gradingScaleExists && !this.isBonus) {
            examStatistics.meanPointsPassed = mean(studentPointsPassed);
            examStatistics.medianPassed = median(studentPointsPassed);
            examStatistics.standardDeviationPassed = standardDeviation(studentPointsPassed);
            examStatistics.noOfExamsFilteredForPassed = studentPointsPassed.length;
            if (this.examScoreDTO.maxPoints) {
                examStatistics.meanPointsRelativePassed = (examStatistics.meanPointsPassed / this.examScoreDTO.maxPoints) * 100;
                examStatistics.medianRelativePassed = (examStatistics.medianPassed / this.examScoreDTO.maxPoints) * 100;
                examStatistics.meanGradePassed = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, examStatistics.meanPointsRelativePassed)!.gradeName;
                examStatistics.medianGradePassed = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, examStatistics.medianRelativePassed)!.gradeName;
                examStatistics.standardGradeDeviationPassed = this.hasNumericGrades ? standardDeviation(studentGradesPassed) : undefined;
            }
            // Calculate statistics for the first assessments of passed exams if second correction exists
            if (this.hasSecondCorrectionAndStarted) {
                examStatistics.meanPointsPassedInFirstCorrection = mean(studentPointsPassedInFirstCorrectionRound);
                examStatistics.medianPassedInFirstCorrection = median(studentPointsPassedInFirstCorrectionRound);
                examStatistics.standardDeviationPassedInFirstCorrection = standardDeviation(studentPointsPassedInFirstCorrectionRound);
                if (this.examScoreDTO.maxPoints) {
                    examStatistics.meanPointsRelativePassedInFirstCorrection = (examStatistics.meanPointsPassedInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
                    examStatistics.medianRelativePassedInFirstCorrection = (examStatistics.medianPassedInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
                    examStatistics.meanGradePassedInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                        this.gradingScale!.gradeSteps,
                        examStatistics.meanPointsRelativePassedInFirstCorrection,
                    )!.gradeName;
                    examStatistics.medianGradePassedInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                        this.gradingScale!.gradeSteps,
                        examStatistics.medianRelativePassedInFirstCorrection,
                    )!.gradeName;
                    examStatistics.standardGradeDeviationPassedInFirstCorrection = this.hasNumericGrades ? standardDeviation(studentGradesPassedInFirstCorrectionRound) : undefined;
                }
            }
        }
        return examStatistics;
    }

    /**
     * Calculates statistics like mean, median and standard deviation specifically for submitted exams
     */
    private calculateSubmittedExamStatistics(
        examStatistics: AggregatedExamResult,
        studentPointsSubmitted: number[],
        studentPointsSubmittedInFirstCorrectionRound: number[],
        studentGradesSubmitted: number[],
        studentGradesSubmittedInFirstCorrectionRound: number[],
    ): AggregatedExamResult {
        if (studentPointsSubmitted.length) {
            examStatistics.meanPointsSubmitted = mean(studentPointsSubmitted);
            examStatistics.medianSubmitted = median(studentPointsSubmitted);
            examStatistics.standardDeviationSubmitted = standardDeviation(studentPointsSubmitted);
            examStatistics.noOfExamsSubmitted = studentPointsSubmitted.length;
            if (this.examScoreDTO.maxPoints) {
                examStatistics.meanPointsRelativeSubmitted = (examStatistics.meanPointsSubmitted / this.examScoreDTO.maxPoints) * 100;
                examStatistics.medianRelativeSubmitted = (examStatistics.medianSubmitted / this.examScoreDTO.maxPoints) * 100;
                if (this.gradingScaleExists) {
                    examStatistics.meanGradeSubmitted = this.gradingSystemService.findMatchingGradeStep(
                        this.gradingScale!.gradeSteps,
                        examStatistics.meanPointsRelativeSubmitted,
                    )!.gradeName;
                    examStatistics.medianGradeSubmitted = this.gradingSystemService.findMatchingGradeStep(
                        this.gradingScale!.gradeSteps,
                        examStatistics.medianRelativeSubmitted,
                    )!.gradeName;
                    examStatistics.standardGradeDeviationSubmitted = this.hasNumericGrades ? standardDeviation(studentGradesSubmitted) : undefined;
                }
            }
            // Calculate statistics for the first assessments of submitted exams if second correction exists
            if (this.hasSecondCorrectionAndStarted) {
                examStatistics.meanPointsInFirstCorrection = mean(studentPointsSubmittedInFirstCorrectionRound);
                examStatistics.medianInFirstCorrection = median(studentPointsSubmittedInFirstCorrectionRound);
                examStatistics.standardDeviationInFirstCorrection = standardDeviation(studentPointsSubmittedInFirstCorrectionRound);
                if (this.examScoreDTO.maxPoints) {
                    examStatistics.meanPointsRelativeInFirstCorrection = (examStatistics.meanPointsInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
                    examStatistics.medianRelativeInFirstCorrection = (examStatistics.medianInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
                    if (this.gradingScaleExists) {
                        examStatistics.meanGradeInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                            this.gradingScale!.gradeSteps,
                            examStatistics.meanPointsRelativeInFirstCorrection,
                        )!.gradeName;
                        examStatistics.medianGradeInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                            this.gradingScale!.gradeSteps,
                            examStatistics.medianRelativeInFirstCorrection,
                        )!.gradeName;
                        examStatistics.standardGradeDeviationInFirstCorrection = this.hasNumericGrades
                            ? standardDeviation(studentGradesSubmittedInFirstCorrectionRound)
                            : undefined;
                    }
                }
            }
        }
        return examStatistics;
    }

    /**
     * Calculates statistics like mean, median and standard deviation for all exams
     */
    private calculateTotalExamStatistics(
        examStatistics: AggregatedExamResult,
        studentPointsTotal: number[],
        studentPointsTotalInFirstCorrectionRound: number[],
        studentGradesTotal: number[],
        studentGradesTotalInFirstCorrectionRound: number[],
    ): AggregatedExamResult {
        if (studentPointsTotal.length) {
            examStatistics.meanPointsTotal = mean(studentPointsTotal);
            examStatistics.medianTotal = median(studentPointsTotal);
            examStatistics.standardDeviationTotal = standardDeviation(studentPointsTotal);
            examStatistics.noOfRegisteredUsers = this.studentResults.length;
            if (this.examScoreDTO.maxPoints) {
                examStatistics.meanPointsRelativeTotal = (examStatistics.meanPointsTotal / this.examScoreDTO.maxPoints) * 100;
                examStatistics.medianRelativeTotal = (examStatistics.medianTotal / this.examScoreDTO.maxPoints) * 100;
                if (this.gradingScaleExists) {
                    examStatistics.meanGradeTotal = this.gradingSystemService.findMatchingGradeStep(
                        this.gradingScale!.gradeSteps,
                        examStatistics.meanPointsRelativeTotal,
                    )!.gradeName;
                    examStatistics.medianGradeTotal = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, examStatistics.medianRelativeTotal)!.gradeName;
                    examStatistics.standardGradeDeviationTotal = this.hasNumericGrades ? standardDeviation(studentGradesTotal) : undefined;
                }
            }
            // Calculate total statistics if second correction exists
            if (this.hasSecondCorrectionAndStarted) {
                examStatistics.meanPointsTotalInFirstCorrection = mean(studentPointsTotalInFirstCorrectionRound);
                examStatistics.medianTotalInFirstCorrection = median(studentPointsTotalInFirstCorrectionRound);
                examStatistics.standardDeviationTotalInFirstCorrection = standardDeviation(studentPointsTotalInFirstCorrectionRound);
                if (this.examScoreDTO.maxPoints) {
                    examStatistics.meanPointsRelativeTotalInFirstCorrection = (examStatistics.meanPointsTotalInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
                    examStatistics.medianRelativeTotalInFirstCorrection = (examStatistics.medianTotalInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
                    if (this.gradingScaleExists) {
                        examStatistics.meanGradeTotalInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                            this.gradingScale!.gradeSteps,
                            examStatistics.meanPointsRelativeTotalInFirstCorrection,
                        )!.gradeName;
                        examStatistics.medianGradeTotalInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                            this.gradingScale!.gradeSteps,
                            examStatistics.medianRelativeTotalInFirstCorrection,
                        )!.gradeName;
                        examStatistics.standardGradeDeviationTotalInFirstCorrection = this.hasNumericGrades
                            ? standardDeviation(studentGradesTotalInFirstCorrectionRound)
                            : undefined;
                    }
                }
            }
        }
        return examStatistics;
    }

    sortRows() {
        this.sortService.sortByProperty(this.examScoreDTO.studentResults, this.predicate, this.reverse);
        this.changeDetector.detectChanges();
    }

    /**
     * Method for exporting exam results
     * @param customCsvOptions If present, a CSV file is exported, otherwise an Excel file.
     */
    exportExamResults(customCsvOptions?: CsvExportOptions) {
        const headers = this.generateExportColumnNames();

        const rows = this.studentResults.map((studentResult) => {
            return this.convertToExportRow(studentResult, customCsvOptions);
        });

        if (customCsvOptions) {
            // required because the currently used library for exporting to csv does not quote the header fields (keys)
            const quotedKeys = headers.map((header) => customCsvOptions.quoteStrings + header + customCsvOptions.quoteStrings);
            this.exportAsCsv(quotedKeys, rows, customCsvOptions);
        } else {
            this.exportAsExcel(headers, rows);
        }
    }

    /**
     * Builds an Excel workbook and starts the download.
     * @param keys The column names used for the export.
     * @param rows The data rows that should be part of the Excel file.
     */
    exportAsExcel(keys: string[], rows: ExportRow[]) {
        const workbook = XLSX.utils.book_new();
        const ws = XLSX.utils.json_to_sheet(rows, { header: keys });
        const worksheetName = 'Exam Scores';
        XLSX.utils.book_append_sheet(workbook, ws, worksheetName);

        const workbookProps = {
            Title: `${this.examScoreDTO.title} Scores`,
            Author: `Artemis ${VERSION ?? ''}`,
        };
        const fileName = `${this.examScoreDTO.title} Exam Results.xlsx`;
        XLSX.writeFile(workbook, fileName, { Props: workbookProps, compression: true });
    }

    /**
     * Builds the CSV from the rows and starts the download.
     * @param headers The column names of the CSV.
     * @param rows The data rows that should be part of the CSV.
     * @param customOptions Custom csv options that should be used for export.
     */
    exportAsCsv(headers: string[], rows: ExportRow[], customOptions: CsvExportOptions) {
        const options = {
            showLabels: true,
            showTitle: false,
            filename: `${this.examScoreDTO.title} Exam Results`,
            useTextFile: false,
            useBom: true,
            headers,
        };

        const combinedOptions = Object.assign(options, customOptions);
        const csvExporter = new ExportToCsv(combinedOptions);
        csvExporter.generateCsv(rows);
    }

    /**
     * Localizes a number, e.g. switching the decimal separator
     */
    localize(numberToLocalize: number): string {
        return this.localeConversionService.toLocaleString(numberToLocalize, this.course!.accuracyOfScores!);
    }

    /**
     * Generates the list of columns that should be part of the exported file.
     * @private
     */
    private generateExportColumnNames(): Array<string> {
        const headers = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
        this.exerciseGroups.forEach((exerciseGroup) => {
            headers.push(`${exerciseGroup.title} ${EXAM_ASSIGNED_EXERCISE}`);
            headers.push(`${exerciseGroup.title} ${EXAM_ACHIEVED_POINTS}`);
            headers.push(`${exerciseGroup.title} ${EXAM_ACHIEVED_SCORE}`);
        });
        headers.push(EXAM_OVERALL_POINTS_KEY);
        headers.push(EXAM_OVERALL_SCORE_KEY);
        if (this.gradingScaleExists) {
            headers.push(this.isBonus ? BONUS_KEY : GRADE_KEY);
            if (this.hasBonus) {
                headers.push(BONUS_GRADE_KEY);
                headers.push(FINAL_GRADE_KEY);
            }
        }
        headers.push(EXAM_SUBMITTED);
        if (this.gradingScaleExists && !this.isBonus) {
            headers.push(EXAM_PASSED);
        }

        if (this.hasPlagiarismVerdicts) {
            headers.push(PLAGIARISM_VERDICT_KEY);
        }

        if (this.hasPlagiarismVerdictsInBonusSource) {
            headers.push(PLAGIARISM_VERDICT_IN_BONUS_SOURCE_KEY);
        }

        return headers;
    }

    /**
     * Constructs a new export row builder for an export row.
     * @param csvExportOptions If present, constructs a CSV row builder with these options, otherwise an Excel row builder is returned.
     * @private
     */
    private newRowBuilder(csvExportOptions?: CsvExportOptions): ExportRowBuilder {
        if (csvExportOptions) {
            return new CsvExportRowBuilder(csvExportOptions.decimalSeparator, this.course?.accuracyOfScores);
        } else {
            return new ExcelExportRowBuilder(this.course?.accuracyOfScores);
        }
    }

    /**
     * Generates the export rows from a student's result
     * @param studentResult
     * @param csvExportOptions If present, this method generates a CSV row with these options, otherwise an Excel row is returned.
     * @private
     */
    private convertToExportRow(studentResult: StudentResult, csvExportOptions?: CsvExportOptions): ExportRow {
        const rowData = this.newRowBuilder(csvExportOptions);

        rowData.setUserInformation(studentResult.name, studentResult.login, studentResult.email, studentResult.registrationNumber);

        this.exerciseGroups.forEach((exerciseGroup) => {
            const exerciseResult = studentResult.exerciseGroupIdToExerciseResult?.[exerciseGroup.id];
            if (exerciseResult) {
                rowData.set(`${exerciseGroup.title} ${EXAM_ASSIGNED_EXERCISE}`, exerciseResult.title);
                rowData.setPoints(`${exerciseGroup.title} ${EXAM_ACHIEVED_POINTS}`, exerciseResult.achievedPoints);
                rowData.setScore(`${exerciseGroup.title} ${EXAM_ACHIEVED_SCORE}`, exerciseResult.achievedScore);
            } else {
                rowData.set(`${exerciseGroup.title} ${EXAM_ASSIGNED_EXERCISE}`, '');
                rowData.set(`${exerciseGroup.title} ${EXAM_ACHIEVED_POINTS}`, '');
                rowData.set(`${exerciseGroup.title} ${EXAM_ACHIEVED_SCORE}`, '');
            }
        });

        rowData.setPoints(EXAM_OVERALL_POINTS_KEY, studentResult.overallPointsAchieved);
        rowData.setScore(EXAM_OVERALL_SCORE_KEY, studentResult.overallScoreAchieved);
        if (this.gradingScaleExists) {
            rowData.set(this.isBonus ? BONUS_KEY : GRADE_KEY, studentResult.overallGrade);
            if (this.hasBonus) {
                rowData.set(BONUS_GRADE_KEY, studentResult.gradeWithBonus?.bonusGrade);
                rowData.set(FINAL_GRADE_KEY, studentResult.gradeWithBonus?.finalGrade ?? studentResult.overallGrade);
            }
        }
        rowData.set(EXAM_SUBMITTED, studentResult.submitted ? 'yes' : 'no');
        if (this.gradingScaleExists && !this.isBonus) {
            rowData.set(EXAM_PASSED, studentResult.hasPassed ? 'yes' : 'no');
        }

        if (this.hasPlagiarismVerdicts) {
            rowData.set(PLAGIARISM_VERDICT_KEY, studentResult.mostSeverePlagiarismVerdict);
        }

        if (this.hasPlagiarismVerdictsInBonusSource) {
            rowData.set(PLAGIARISM_VERDICT_IN_BONUS_SOURCE_KEY, studentResult.gradeWithBonus?.mostSeverePlagiarismVerdict);
        }
        return rowData.build();
    }

    /**
     * Rounds given points according to the course specific rounding settings
     * @param points the points that should be rounded
     * @returns localized string representation of the rounded points
     */
    roundAndPerformLocalConversion(points: number | undefined): string {
        return this.localize(roundValueSpecifiedByCourseSettings(points, this.course));
    }

    /**
     * This method compares the exam scores computed via the two approaches on the server (one using
     * participation -> submission -> result and the other one using the participationScores table)
     * In the future we might switch to the server side method, so we use this method to detect discrepancies.
     * @param examScoreDTOs the exam scores sent from the server (new calculation method)
     */
    private compareNewExamScoresCalculationWithOldCalculation(examScoreDTOs: ScoresDTO[]) {
        if (!this.studentResults || !examScoreDTOs) {
            return;
        }
        for (const examScoreDTO of examScoreDTOs) {
            this.studentIdToExamScoreDTOs.set(examScoreDTO.studentId!, examScoreDTO);
        }
        for (const studentResult of this.studentResults) {
            const overAllPoints = roundValueSpecifiedByCourseSettings(studentResult.overallPointsAchieved, this.course);
            const overallScore = roundValueSpecifiedByCourseSettings(studentResult.overallScoreAchieved, this.course);

            const regularCalculation = {
                scoreAchieved: overallScore,
                pointsAchieved: overAllPoints,
                userId: studentResult.userId,
                userLogin: studentResult.login,
            };
            // checking if the same as in the exam scores map
            const examScoreDTO = this.studentIdToExamScoreDTOs.get(studentResult.userId);
            if (!examScoreDTO) {
                const errorMessage = `Exam scores not included in new calculation: ${JSON.stringify(regularCalculation)}`;
                this.logErrorOnSentry(errorMessage);
            } else {
                examScoreDTO.scoreAchieved = roundValueSpecifiedByCourseSettings(examScoreDTO.scoreAchieved, this.course);
                examScoreDTO.pointsAchieved = roundValueSpecifiedByCourseSettings(examScoreDTO.pointsAchieved, this.course);

                if (Math.abs(examScoreDTO.pointsAchieved - regularCalculation.pointsAchieved) > 0.1) {
                    const errorMessage = `Different exam points in new calculation. Regular Calculation: ${JSON.stringify(regularCalculation)}. New Calculation: ${JSON.stringify(
                        examScoreDTO,
                    )}`;
                    this.logErrorOnSentry(errorMessage);
                }
                if (Math.abs(examScoreDTO.scoreAchieved - regularCalculation.scoreAchieved) > 0.1) {
                    const errorMessage = `Different exam score in new calculation. Regular Calculation: ${JSON.stringify(regularCalculation)}. New Calculation : ${JSON.stringify(
                        examScoreDTO,
                    )}`;
                    this.logErrorOnSentry(errorMessage);
                }
            }
        }
    }

    logErrorOnSentry(errorMessage: string) {
        captureException(new Error(errorMessage));
    }

    /**
     * Formats the datalabel for every bar in order to satisfy the following pattern:
     * number of submissions (percentage of submissions)
     * @param submissionCount the number of submissions that fall in the grading step
     * @returns string containing the number of submissions + (percentage of submissions)
     */
    formatDataLabel(submissionCount: number): string {
        const percentage = this.noOfExamsFiltered && this.noOfExamsFiltered > 0 ? this.roundAndPerformLocalConversion((submissionCount * 100) / this.noOfExamsFiltered) : 0;
        return submissionCount + ' (' + percentage + '%)';
    }

    /**
     * Handles the click event on a chart bar. The user is then delegated to the participant scores page of the exam
     */
    onSelect() {
        if (this.accountService.hasAnyAuthorityDirect([Authority.INSTRUCTOR])) {
            this.navigationUtilService.routeInNewTab(['course-management', this.course!.id, 'exams', this.examScoreDTO.examId, 'participant-scores']);
        }
    }

    /**
     * Method that handles the toggling of a median highlighting in the chart.
     * If no grading scale exists, the user can only toggle the overall score median.
     * If a grading scale exists, the user can switch between the overall score median and the median of the scores of all passed exams.
     * Per default, the latter is selected in this case.
     * @param medianType an enum indicating if the user toggles the overall median or the passed median
     */
    toggleMedian(medianType: MedianType): void {
        switch (medianType) {
            case MedianType.PASSED:
                this.showPassedMedian = !this.showPassedMedian;
                // The user selects the passed median to be highlighted, therefore we deactivate the highlighting of the other one
                if (this.showPassedMedian) {
                    this.showOverallMedian = false;
                }
                break;
            case MedianType.OVERALL:
            case MedianType.SUBMITTED:
                this.showOverallMedian = !this.showOverallMedian;
                // The user selects the overall median to be highlighted, therefore we deactivate the highlighting of the other one
                if (this.showOverallMedian) {
                    this.showPassedMedian = false;
                }
                break;
        }
        if (this.showPassedMedian || this.showOverallMedian) {
            this.determineAndHighlightChartMedian(medianType);
        } else {
            this.highlightedValue = undefined;
        }
    }

    /**
     * Auxiliary method that determines the median to be highlighted in the chart
     * It identifies the bar representing the corresponding median type and
     * highlights it by making all other chart bars a bit more transparent
     * @param medianType enum representing the type of median to be highlighted
     * @private
     */
    private determineAndHighlightChartMedian(medianType: MedianType): void {
        let chartMedian;
        this.lastCalculatedMedianType = medianType;
        if (medianType === MedianType.PASSED) {
            const passedMedian = this.aggregatedExamResults.medianRelativePassed;
            chartMedian = passedMedian ? roundValueSpecifiedByCourseSettings(passedMedian, this.course) : 0;
            this.showPassedMedian = true;
        } else {
            this.setOverallChartMedianDependingOfExamsIncluded(medianType);
            chartMedian = this.overallChartMedian;
            this.showOverallMedian = true;
        }
        this.highlightedValue = chartMedian;
        this.changeDetector.detectChanges();
    }

    /**
     * Auxiliary method that sets overallChartMedian depending on if only submitted exams are included or not
     * @param medianType enum indicating if the median of all exams should be shown or only of submitted exams
     * @private
     */
    private setOverallChartMedianDependingOfExamsIncluded(medianType: MedianType): void {
        if (medianType === MedianType.OVERALL) {
            const overallMedian = this.aggregatedExamResults.medianRelativeTotal;
            this.overallChartMedian = overallMedian ? roundValueSpecifiedByCourseSettings(overallMedian, this.course) : 0;
        } else {
            const submittedMedian = this.aggregatedExamResults.medianRelativeSubmitted;
            this.overallChartMedian = submittedMedian ? roundValueSpecifiedByCourseSettings(submittedMedian, this.course) : 0;
        }
        this.overallChartMedianType = medianType;
    }

    /**
     * Auxiliary method that updates the statistics table above the score distribution depending on the current filter state
     * The filter of interest is determined by the two boolean flags {@link ExamScoresComponent#filterForSubmittedExams} and
     * {@link ExamScoresComponent#filterForNonEmptySubmissions}
     * @private
     */
    private updateValuesAccordingToFilter(): void {
        this.tableState.absoluteAmountOfSubmittedExams = this.aggregatedExamResults.noOfExamsSubmitted;
        this.tableState.absoluteAmountOfTotalExams = this.aggregatedExamResults.noOfRegisteredUsers;
        let denominator: number;
        if (this.filterForSubmittedExams && this.filterForNonEmptySubmissions) {
            denominator = this.aggregatedExamResults.noOfExamsSubmittedAndNotEmpty;
            this.tableState.absoluteAmountOfSubmittedExams = this.aggregatedExamResults.noOfExamsSubmittedAndNotEmpty;
            this.tableState.absoluteAmountOfTotalExams = this.aggregatedExamResults.noOfExamsSubmittedAndNotEmpty;
            this.determineSubmittedAndNonEmptyValues();
            this.setValuesForSubmittedAndNonEmptyFilter(this.tableState);
        } else if (this.filterForSubmittedExams && !this.filterForNonEmptySubmissions) {
            denominator = this.aggregatedExamResults.noOfExamsSubmitted;
            this.tableState.absoluteAmountOfTotalExams = this.aggregatedExamResults.noOfExamsSubmitted;
            this.setValuesForSubmittedFilter(this.tableState);
        } else if (!this.filterForSubmittedExams && this.filterForNonEmptySubmissions) {
            denominator = this.aggregatedExamResults.noOfExamsNonEmpty;
            this.tableState.absoluteAmountOfSubmittedExams = this.aggregatedExamResults.noOfExamsSubmittedAndNotEmpty;
            this.tableState.absoluteAmountOfTotalExams = this.aggregatedExamResults.noOfExamsNonEmpty;
            this.determineNonEmptyValues();
            this.determineSubmittedAndNonEmptyValues();
            this.setValuesForNonEmptyFilter(this.tableState);
        } else {
            denominator = this.aggregatedExamResults.noOfRegisteredUsers;
            this.setValuesForNoFilter(this.tableState);
        }

        this.tableState.relativeAmountOfPassedExams =
            denominator > 0 ? this.roundAndPerformLocalConversion((this.aggregatedExamResults.noOfExamsFilteredForPassed / denominator) * 100) : '-';
        this.tableState.relativeAmountOfSubmittedExams =
            denominator > 0 ? this.roundAndPerformLocalConversion((this.tableState.absoluteAmountOfSubmittedExams / denominator) * 100) : '-';
    }

    /**
     * Auxiliary method that sets the variants including only submitted AND not empty exams for all affected statistical values
     * @private
     */
    private determineSubmittedAndNonEmptyValues(): void {
        // If one value is not undefined, all other values have been computed as well and we take the cached results instead of recalculating every time
        if (this.aggregatedExamResults.meanPointsSubmittedAndNonEmpty) {
            return;
        }
        const overallPointsSubmittedAndNonEmpty: number[] = [];
        const pointsSubmittedAndNonEmptyInFirstCorrection: number[] = [];
        let submittedAndNonEmptyGrades: number[] = [];
        let submittedAndNonEmptyGradesInFirstCorrection: number[] = [];
        this.studentResults.forEach((result) => {
            if (result.exerciseGroupIdToExerciseResult) {
                const hasAtLeastOneSubmission = Object.entries(result.exerciseGroupIdToExerciseResult).some(([, exerciseResult]) => exerciseResult.hasNonEmptySubmission);
                if (result.submitted && hasAtLeastOneSubmission) {
                    overallPointsSubmittedAndNonEmpty.push(result.overallPointsAchieved ?? 0);
                    submittedAndNonEmptyGrades = this.collectOverallGrades(submittedAndNonEmptyGrades, result);
                    if (this.hasSecondCorrectionAndStarted) {
                        pointsSubmittedAndNonEmptyInFirstCorrection.push(result.overallPointsAchievedInFirstCorrection ?? 0);
                        submittedAndNonEmptyGradesInFirstCorrection = this.collectOverallGradesInFirstCorrection(submittedAndNonEmptyGradesInFirstCorrection, result);
                    }
                }
            }
        });
        this.determineMeanMedianAndStandardDeviationSubmittedAndNonEmpty(overallPointsSubmittedAndNonEmpty);
        this.determineMeanMedianAndStandardDeviationSubmittedAndNonEmptyInFirstCorrection(pointsSubmittedAndNonEmptyInFirstCorrection);
        if (this.gradingScaleExists && !this.isBonus) {
            this.determineGradesSubmittedAndNonEmpty(overallPointsSubmittedAndNonEmpty.length > 0);
            if (this.hasNumericGrades) {
                this.aggregatedExamResults.standardGradeDeviationSubmittedAndNonEmpty =
                    submittedAndNonEmptyGrades.length > 0 ? standardDeviation(submittedAndNonEmptyGrades) : undefined;
                if (this.hasSecondCorrectionAndStarted) {
                    this.aggregatedExamResults.standardGradeDeviationSubmittedAndNonEmptyInFirstCorrection =
                        submittedAndNonEmptyGradesInFirstCorrection.length > 0 ? standardDeviation(submittedAndNonEmptyGradesInFirstCorrection) : undefined;
                }
            }
        }
    }

    /**
     * Sets mean and median points and scores and the standard deviation in {@link ExamScoresComponent#aggregatedExamResults} if only submitted and non empty
     * student exams are considered
     * @param overallPointsSubmittedAndNonEmpty array containing the overall points of every submitted and non-empty student exam
     * @private
     */
    private determineMeanMedianAndStandardDeviationSubmittedAndNonEmpty(overallPointsSubmittedAndNonEmpty: number[]): void {
        if (overallPointsSubmittedAndNonEmpty.length > 0) {
            this.aggregatedExamResults.meanPointsSubmittedAndNonEmpty = mean(overallPointsSubmittedAndNonEmpty);
            this.aggregatedExamResults.medianSubmittedAndNonEmpty = median(overallPointsSubmittedAndNonEmpty);
            if (this.examScoreDTO.maxPoints) {
                this.aggregatedExamResults.meanScoreSubmittedAndNonEmpty = (this.aggregatedExamResults.meanPointsSubmittedAndNonEmpty / this.examScoreDTO.maxPoints) * 100;
                this.aggregatedExamResults.medianScoreSubmittedAndNonEmpty = (this.aggregatedExamResults.medianSubmittedAndNonEmpty / this.examScoreDTO.maxPoints) * 100;
            }
            this.aggregatedExamResults.standardDeviationSubmittedAndNonEmpty = standardDeviation(overallPointsSubmittedAndNonEmpty);
        }
    }

    /**
     * Sets mean and median points and scores and the standard deviation in {@link ExamScoresComponent#aggregatedExamResults} after first correction
     * if only submitted and non empty student exams are considered
     * @param pointsSubmittedAndNonEmptyInFirstCorrection array containing the overall points of every submitted and non-empty student exam after the first correction round
     * @private
     */
    private determineMeanMedianAndStandardDeviationSubmittedAndNonEmptyInFirstCorrection(pointsSubmittedAndNonEmptyInFirstCorrection: number[]): void {
        if (this.hasSecondCorrectionAndStarted && pointsSubmittedAndNonEmptyInFirstCorrection.length > 0) {
            this.aggregatedExamResults.meanPointsSubmittedAndNonEmptyInFirstCorrection = mean(pointsSubmittedAndNonEmptyInFirstCorrection);
            this.aggregatedExamResults.medianSubmittedAndNonEmptyInFirstCorrection = median(pointsSubmittedAndNonEmptyInFirstCorrection);
            if (this.examScoreDTO.maxPoints) {
                this.aggregatedExamResults.meanScoreSubmittedAndNonEmptyInFirstCorrection =
                    (this.aggregatedExamResults.meanPointsSubmittedAndNonEmptyInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
                this.aggregatedExamResults.medianScoreSubmittedAndNonEmptyInFirstCorrection =
                    (this.aggregatedExamResults.medianSubmittedAndNonEmptyInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
            }
            this.aggregatedExamResults.standardDeviationSubmittedAndNonEmptyInFirstCorrection = standardDeviation(pointsSubmittedAndNonEmptyInFirstCorrection);
        }
    }

    /**
     * Sets mean and median grades in {@link ExamScoresComponent#aggregatedExamResults} if only submitted and non empty
     * student exams are considered.
     * This includes the corresponding grades after the first correction round if appropriate
     * @param atLeastOneExam indicates whether at least one student exam has been submitted and is not empty
     * @private
     */
    private determineGradesSubmittedAndNonEmpty(atLeastOneExam: boolean): void {
        if (atLeastOneExam) {
            this.aggregatedExamResults.meanGradeSubmittedAndNonEmpty = this.gradingSystemService.findMatchingGradeStep(
                this.gradingScale!.gradeSteps,
                this.aggregatedExamResults.meanScoreSubmittedAndNonEmpty,
            )!.gradeName;

            this.aggregatedExamResults.medianGradeSubmittedAndNonEmpty = this.gradingSystemService.findMatchingGradeStep(
                this.gradingScale!.gradeSteps,
                this.aggregatedExamResults.medianScoreSubmittedAndNonEmpty,
            )!.gradeName;
            if (this.hasSecondCorrectionAndStarted) {
                this.aggregatedExamResults.meanGradeSubmittedAndNonEmptyInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                    this.gradingScale!.gradeSteps,
                    this.aggregatedExamResults.meanScoreSubmittedAndNonEmptyInFirstCorrection,
                )!.gradeName;

                this.aggregatedExamResults.medianGradeSubmittedAndNonEmptyInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                    this.gradingScale!.gradeSteps,
                    this.aggregatedExamResults.medianScoreSubmittedAndNonEmptyInFirstCorrection!,
                )!.gradeName;
            }
        }
    }

    /**
     * Auxiliary method that sets the variants including only not empty exams for all affected statistical values
     * @private
     */
    private determineNonEmptyValues(): void {
        if (this.aggregatedExamResults.meanPointsNonEmpty) {
            return;
        }
        const overallPointsNonEmpty: number[] = [];
        const pointsNonEmptyInFirstCorrection: number[] = [];
        let nonEmptyGrades: number[] = [];
        let nonEmptyGradesInFirstCorrection: number[] = [];
        this.studentResults.forEach((result) => {
            if (result.exerciseGroupIdToExerciseResult) {
                const hasAtLeastOneSubmission = Object.entries(result.exerciseGroupIdToExerciseResult).some(([, exerciseResult]) => exerciseResult.hasNonEmptySubmission);
                if (hasAtLeastOneSubmission) {
                    overallPointsNonEmpty.push(result.overallPointsAchieved ?? 0);
                    nonEmptyGrades = this.collectOverallGrades(nonEmptyGrades, result);
                    if (this.hasSecondCorrectionAndStarted) {
                        pointsNonEmptyInFirstCorrection.push(result.overallPointsAchievedInFirstCorrection ?? 0);
                        nonEmptyGradesInFirstCorrection = this.collectOverallGradesInFirstCorrection(nonEmptyGradesInFirstCorrection, result);
                    }
                }
            }
        });
        this.determineMeanMedianAndStandardDeviationNonEmpty(overallPointsNonEmpty);
        this.determineMeanMedianAndStandardDeviationNonEmptyInFirstCorrection(pointsNonEmptyInFirstCorrection);
        if (this.gradingScale && !this.isBonus) {
            this.determineGradesNonEmpty(overallPointsNonEmpty.length > 0);
            if (this.hasNumericGrades) {
                this.aggregatedExamResults.standardGradeDeviationNonEmpty = nonEmptyGrades.length > 0 ? standardDeviation(nonEmptyGrades) : undefined;
                if (this.hasSecondCorrectionAndStarted) {
                    this.aggregatedExamResults.standardGradeDeviationNonEmptyInFirstCorrection =
                        nonEmptyGradesInFirstCorrection.length > 0 ? standardDeviation(nonEmptyGradesInFirstCorrection) : undefined;
                }
            }
        }
    }

    /**
     * Sets mean and median points and scores and the standard deviation in {@link ExamScoresComponent#aggregatedExamResults} if only non empty
     * student exams are considered
     * @param overallPointsNonEmpty array containing the overall points of every non-empty student exam
     * @private
     */
    private determineMeanMedianAndStandardDeviationNonEmpty(overallPointsNonEmpty: number[]): void {
        if (overallPointsNonEmpty.length > 0) {
            this.aggregatedExamResults.meanPointsNonEmpty = mean(overallPointsNonEmpty);
            this.aggregatedExamResults.medianNonEmpty = median(overallPointsNonEmpty);
            if (this.examScoreDTO.maxPoints) {
                this.aggregatedExamResults.meanScoreNonEmpty = (this.aggregatedExamResults.meanPointsNonEmpty / this.examScoreDTO.maxPoints) * 100;
                this.aggregatedExamResults.medianScoreNonEmpty = (this.aggregatedExamResults.medianNonEmpty / this.examScoreDTO.maxPoints) * 100;
            }
            this.aggregatedExamResults.standardDeviationNonEmpty = standardDeviation(overallPointsNonEmpty);
        }
    }

    /**
     * Sets mean and median points and scores and the standard deviation in {@link ExamScoresComponent#aggregatedExamResults} after first correction
     * if only non empty student exams are considered
     * @param pointsNonEmptyInFirstCorrection array containing the overall points of every non-empty student exam after the first correction round
     * @private
     */
    private determineMeanMedianAndStandardDeviationNonEmptyInFirstCorrection(pointsNonEmptyInFirstCorrection: number[]): void {
        if (this.hasSecondCorrectionAndStarted && pointsNonEmptyInFirstCorrection.length > 0) {
            this.aggregatedExamResults.meanPointsNonEmptyInFirstCorrection = mean(pointsNonEmptyInFirstCorrection);
            this.aggregatedExamResults.medianNonEmptyInFirstCorrection = median(pointsNonEmptyInFirstCorrection);
            if (this.examScoreDTO.maxPoints) {
                this.aggregatedExamResults.meanScoreNonEmptyInFirstCorrection =
                    (this.aggregatedExamResults.meanPointsNonEmptyInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
                this.aggregatedExamResults.medianScoreNonEmptyInFirstCorrection = (this.aggregatedExamResults.medianNonEmptyInFirstCorrection / this.examScoreDTO.maxPoints) * 100;
            }
            this.aggregatedExamResults.standardDeviationNonEmptyInFirstCorrection = standardDeviation(pointsNonEmptyInFirstCorrection);
        }
    }

    /**
     * Sets mean and median grades in {@link ExamScoresComponent#aggregatedExamResults} if only non empty
     * student exams are considered.
     * This includes the corresponding grades after the first correction round if appropriate
     * @param atLeastOneExam indicates whether at least one student exam is not empty
     * @private
     */
    private determineGradesNonEmpty(atLeastOneExam: boolean): void {
        if (atLeastOneExam) {
            this.aggregatedExamResults.meanGradeNonEmpty = this.gradingSystemService.findMatchingGradeStep(
                this.gradingScale!.gradeSteps,
                this.aggregatedExamResults.meanScoreNonEmpty,
            )!.gradeName;

            this.aggregatedExamResults.medianGradeNonEmpty = this.gradingSystemService.findMatchingGradeStep(
                this.gradingScale!.gradeSteps,
                this.aggregatedExamResults.medianScoreNonEmpty,
            )!.gradeName;
            if (this.hasSecondCorrectionAndStarted) {
                this.aggregatedExamResults.meanGradeNonEmptyInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                    this.gradingScale!.gradeSteps,
                    this.aggregatedExamResults.meanScoreNonEmptyInFirstCorrection,
                )!.gradeName;

                this.aggregatedExamResults.medianGradeNonEmptyInFirstCorrection = this.gradingSystemService.findMatchingGradeStep(
                    this.gradingScale!.gradeSteps,
                    this.aggregatedExamResults.medianScoreNonEmptyInFirstCorrection,
                )!.gradeName;
            }
        }
    }

    /**
     * Auxiliary method in order to collect all numeric overall grades for the exam
     * @param grades the currently collected overall grades
     * @param result the result containing a numeric or not numeric overall grade
     * @private
     * @returns updated array of collected grades
     */
    private collectOverallGrades(grades: number[], result: StudentResult): number[] {
        if (this.gradingScaleExists && this.hasNumericGrades) {
            grades.push(Number(result.overallGrade));
        }
        return grades;
    }

    /**
     * Auxiliary method in order to collect all numeric grades after first correction round for the exam
     * @param grades the currently collected grades after first correction round
     * @param result the result containing a numeric or not numeric grade after first correction round
     * @private
     * @returns updated array of collected grades
     */
    private collectOverallGradesInFirstCorrection(grades: number[], result: StudentResult): number[] {
        if (this.gradingScaleExists && this.hasNumericGrades) {
            grades.push(Number(result.overallGradeInFirstCorrection));
        }
        return grades;
    }

    /**
     * Sets the corresponding values of {@link ExamScoresComponent#aggregatedExamResults} to the table state if both filter options are activated
     * @param tableState object containing the values currently displayed by the table
     * @private
     */
    private setValuesForSubmittedAndNonEmptyFilter(tableState: TableState): void {
        this.setAverageValuesForSubmittedAndNonEmptyFilter(tableState);
        this.setMedianValuesForSubmittedAndNonEmptyFilter(tableState);
        this.setStandardDeviationForSubmittedAndNonEmptyFilter(tableState);
    }

    /**
     * Sets all average values to the table state if both filter options are activated
     * @param tableState the table state that should be updated
     * @private
     */
    private setAverageValuesForSubmittedAndNonEmptyFilter(tableState: TableState): void {
        const averagePointsSubmittedAndNonEmpty = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsSubmittedAndNonEmpty);
        const averagePointsSubmittedAndNonEmptyInFirstCorrection = this.roundAndLocalizeStatisticalValue(
            this.aggregatedExamResults.meanPointsSubmittedAndNonEmptyInFirstCorrection,
        );
        const averageScoreSubmittedAndNonEmpty = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanScoreSubmittedAndNonEmpty);
        const averageScoreSubmittedAndNonEmptyInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanScoreSubmittedAndNonEmptyInFirstCorrection);
        const averageGradeSubmittedAndNonEmpty = this.aggregatedExamResults.meanGradeSubmittedAndNonEmpty ?? '-';
        const averageGradeSubmittedAndNonEmptyInFirstCorrection = this.aggregatedExamResults.meanGradeSubmittedAndNonEmptyInFirstCorrection ?? '-';
        tableState.averagePointsSubmitted = averagePointsSubmittedAndNonEmpty;
        tableState.averagePointsTotal = averagePointsSubmittedAndNonEmpty;
        tableState.averagePointsSubmittedInFirstCorrection = averagePointsSubmittedAndNonEmptyInFirstCorrection;
        tableState.averagePointsTotalInFirstCorrection = averagePointsSubmittedAndNonEmptyInFirstCorrection;
        tableState.averageScoreSubmitted = averageScoreSubmittedAndNonEmpty;
        tableState.averageScoreTotal = averageScoreSubmittedAndNonEmpty;
        tableState.averageScoreSubmittedInFirstCorrection = averageScoreSubmittedAndNonEmptyInFirstCorrection;
        tableState.averageScoreTotalInFirstCorrection = averageScoreSubmittedAndNonEmptyInFirstCorrection;
        tableState.averageGradeSubmitted = averageGradeSubmittedAndNonEmpty;
        tableState.averageGradeTotal = averageGradeSubmittedAndNonEmpty;
        tableState.averageGradeSubmittedInFirstCorrection = averageGradeSubmittedAndNonEmptyInFirstCorrection;
        tableState.averageGradeTotalInFirstCorrection = averageGradeSubmittedAndNonEmptyInFirstCorrection;
    }

    /**
     * Sets all median values to the table state if both filter options are activated
     * @param tableState the table state that should be updated
     * @private
     */
    private setMedianValuesForSubmittedAndNonEmptyFilter(tableState: TableState): void {
        const medianPointsSubmittedAndNonEmpty = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianSubmittedAndNonEmpty);
        const medianPointsSubmittedAndNonEmptyInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianSubmittedAndNonEmptyInFirstCorrection);
        const medianScoreSubmittedAndNonEmpty = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianScoreSubmittedAndNonEmpty);
        const medianScoreSubmittedAndNonEmptyInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianScoreSubmittedAndNonEmptyInFirstCorrection);
        const medianGradeSubmittedAndNonEmpty = this.aggregatedExamResults.medianGradeSubmittedAndNonEmpty ?? '-';
        const medianGradeSubmittedAndNonEmptyInFirstCorrection = this.aggregatedExamResults.medianGradeSubmittedAndNonEmptyInFirstCorrection ?? '-';
        tableState.medianPointsSubmitted = medianPointsSubmittedAndNonEmpty;
        tableState.medianPointsTotal = medianPointsSubmittedAndNonEmpty;
        tableState.medianPointsSubmittedInFirstCorrection = medianPointsSubmittedAndNonEmptyInFirstCorrection;
        tableState.medianPointsTotalInFirstCorrection = medianPointsSubmittedAndNonEmptyInFirstCorrection;
        tableState.medianScoreSubmitted = medianScoreSubmittedAndNonEmpty;
        tableState.medianScoreTotal = medianScoreSubmittedAndNonEmpty;
        tableState.medianScoreSubmittedInFirstCorrection = medianScoreSubmittedAndNonEmptyInFirstCorrection;
        tableState.medianScoreTotalInFirstCorrection = medianScoreSubmittedAndNonEmptyInFirstCorrection;
        tableState.medianGradeSubmitted = medianGradeSubmittedAndNonEmpty;
        tableState.medianGradeTotal = medianGradeSubmittedAndNonEmpty;
        tableState.medianGradeSubmittedInFirstCorrection = medianGradeSubmittedAndNonEmptyInFirstCorrection;
        tableState.medianGradeTotalInFirstCorrection = medianGradeSubmittedAndNonEmptyInFirstCorrection;
    }

    /**
     * Sets all standard deviations to the table state if both filter options are activated
     * @param tableState the table state that should be updated
     * @private
     */
    private setStandardDeviationForSubmittedAndNonEmptyFilter(tableState: TableState): void {
        const standardDeviationSubmittedAndNonEmpty = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationSubmittedAndNonEmpty);
        const standardDeviationSubmittedAndNonEmptyInFirstCorrection = this.roundAndLocalizeStatisticalValue(
            this.aggregatedExamResults.standardDeviationSubmittedAndNonEmptyInFirstCorrection,
        );
        const standardGradeDeviationSubmittedAndNonEmpty = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationSubmittedAndNonEmpty);
        const standardGradeDeviationSubmittedAndNonEmptyInFirstCorrection = this.roundAndLocalizeStatisticalValue(
            this.aggregatedExamResults.standardGradeDeviationSubmittedAndNonEmptyInFirstCorrection,
        );
        tableState.standardDeviationSubmitted = standardDeviationSubmittedAndNonEmpty;
        tableState.standardDeviationTotal = standardDeviationSubmittedAndNonEmpty;
        tableState.standardDeviationSubmittedInFirstCorrection = standardDeviationSubmittedAndNonEmptyInFirstCorrection;
        tableState.standardDeviationTotalInFirstCorrection = standardDeviationSubmittedAndNonEmptyInFirstCorrection;
        tableState.standardGradeDeviationSubmitted = standardGradeDeviationSubmittedAndNonEmpty;
        tableState.standardGradeDeviationTotal = standardGradeDeviationSubmittedAndNonEmpty;
        tableState.standardGradeDeviationSubmittedInFirstCorrection = standardGradeDeviationSubmittedAndNonEmptyInFirstCorrection;
        tableState.standardGradeDeviationTotalInFirstCorrection = standardGradeDeviationSubmittedAndNonEmptyInFirstCorrection;
    }

    /**
     * Sets the corresponding values of {@link ExamScoresComponent#aggregatedExamResults} to the table state if only not empty exams should be included in calculation
     * @param tableState object containing the values currently displayed by the table
     * @private
     */
    private setValuesForNonEmptyFilter(tableState: TableState): void {
        this.setAverageValuesForNonEmptyFilter(tableState);
        this.setMedianValuesForNonEmptyFilter(tableState);
        this.setStandardDeviationForNonEmptyFilter(tableState);
    }

    /**
     * Sets all average values to the table state if only not empty exams should be included in calculation
     * @param tableState the table state that should be updated
     * @private
     */
    private setAverageValuesForNonEmptyFilter(tableState: TableState): void {
        tableState.averagePointsSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsSubmittedAndNonEmpty);
        tableState.averagePointsTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsNonEmpty);
        tableState.averagePointsSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsSubmittedAndNonEmptyInFirstCorrection);
        tableState.averageGradeTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsNonEmptyInFirstCorrection);
        tableState.averageScoreSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanScoreSubmittedAndNonEmpty);
        tableState.averageScoreTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanScoreNonEmpty);
        tableState.averageScoreSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanScoreSubmittedAndNonEmptyInFirstCorrection);
        tableState.averageScoreTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanScoreNonEmptyInFirstCorrection);
        tableState.averageGradeSubmitted = this.aggregatedExamResults.meanGradeSubmittedAndNonEmpty ?? '-';
        tableState.averageGradeTotal = this.aggregatedExamResults.meanGradeNonEmpty ?? '-';
        tableState.averageGradeSubmittedInFirstCorrection = this.aggregatedExamResults.meanGradeSubmittedAndNonEmptyInFirstCorrection ?? '-';
        tableState.averageGradeTotalInFirstCorrection = this.aggregatedExamResults.meanGradeNonEmptyInFirstCorrection ?? '-';
    }

    /**
     * Sets all median values to the table state if only not empty exams should be included in calculation
     * @param tableState the table state that should be updated
     * @private
     */
    private setMedianValuesForNonEmptyFilter(tableState: TableState): void {
        tableState.medianPointsSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianSubmittedAndNonEmpty);
        tableState.medianPointsTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianNonEmpty);
        tableState.medianPointsSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianSubmittedAndNonEmptyInFirstCorrection);
        tableState.medianPointsTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianNonEmptyInFirstCorrection);
        tableState.medianScoreSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianScoreSubmittedAndNonEmpty);
        tableState.medianScoreTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianScoreNonEmpty);
        tableState.medianScoreSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianScoreSubmittedAndNonEmptyInFirstCorrection);
        tableState.medianScoreTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianScoreNonEmptyInFirstCorrection);
        tableState.medianGradeSubmitted = this.aggregatedExamResults.medianGradeSubmittedAndNonEmpty ?? '-';
        tableState.medianGradeTotal = this.aggregatedExamResults.medianGradeNonEmpty ?? '-';
        tableState.medianGradeSubmittedInFirstCorrection = this.aggregatedExamResults.medianGradeSubmittedAndNonEmptyInFirstCorrection ?? '-';
        tableState.medianGradeTotalInFirstCorrection = this.aggregatedExamResults.medianGradeTotalInFirstCorrection ?? '-';
    }

    /**
     * Sets all standard deviations to the table state if only not empty exams should be included in calculation
     * @param tableState the table state that should be updated
     * @private
     */
    private setStandardDeviationForNonEmptyFilter(tableState: TableState): void {
        tableState.standardDeviationSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationSubmittedAndNonEmpty);
        tableState.standardDeviationTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationNonEmpty);
        tableState.standardDeviationSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(
            this.aggregatedExamResults.standardDeviationSubmittedAndNonEmptyInFirstCorrection,
        );
        tableState.standardDeviationTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationNonEmptyInFirstCorrection);
        tableState.standardGradeDeviationSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationSubmittedAndNonEmpty);
        tableState.standardGradeDeviationTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationNonEmpty);
        tableState.standardGradeDeviationSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(
            this.aggregatedExamResults.standardGradeDeviationSubmittedAndNonEmptyInFirstCorrection,
        );
        tableState.standardGradeDeviationTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationNonEmptyInFirstCorrection);
    }

    /**
     * Sets the corresponding values of {@link ExamScoresComponent#aggregatedExamResults} to the table state if only submitted exams should be included in calculation
     * @param tableState object containing the values currently displayed by the table
     * @private
     */
    private setValuesForSubmittedFilter(tableState: TableState): void {
        this.setAverageValuesForSubmittedFilter(tableState);
        this.setMedianValuesForSubmittedFilter(tableState);
        this.setStandardDeviationForSubmittedFilter(tableState);
    }

    /**
     * Sets all average values to the table state if only submitted exams should be included in calculation
     * @param tableState the table state that should be updated
     * @private
     */
    private setAverageValuesForSubmittedFilter(tableState: TableState): void {
        const averagePointsSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsSubmitted);
        const averagePointsSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsInFirstCorrection);
        const averageScoreSubmitted = this.roundAndPerformLocalConversion(this.aggregatedExamResults.meanPointsRelativeSubmitted);
        const averageScoreSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsRelativeSubmitted);
        const averageGradeSubmitted = this.aggregatedExamResults.meanGradeSubmitted ?? '-';
        const averageGradeSubmittedInFirstCorrectionRound = this.aggregatedExamResults.meanGradeInFirstCorrection ?? '-';
        tableState.averagePointsSubmitted = averagePointsSubmitted;
        tableState.averagePointsTotal = averagePointsSubmitted;
        tableState.averagePointsSubmittedInFirstCorrection = averagePointsSubmittedInFirstCorrection;
        tableState.averagePointsTotalInFirstCorrection = averagePointsSubmittedInFirstCorrection;
        tableState.averageScoreSubmitted = averageScoreSubmitted;
        tableState.averageScoreTotal = averageScoreSubmitted;
        tableState.averageScoreSubmittedInFirstCorrection = averageScoreSubmittedInFirstCorrection;
        tableState.averageScoreTotalInFirstCorrection = averageScoreSubmittedInFirstCorrection;
        tableState.averageGradeSubmitted = averageGradeSubmitted;
        tableState.averageGradeTotal = averageGradeSubmitted;
        tableState.averageGradeSubmittedInFirstCorrection = averageGradeSubmittedInFirstCorrectionRound;
        tableState.averageGradeTotalInFirstCorrection = averageGradeSubmittedInFirstCorrectionRound;
    }

    /**
     * Sets all median values to the table state if only submitted exams should be included in calculation
     * @param tableState the table state that should be updated
     * @private
     */
    private setMedianValuesForSubmittedFilter(tableState: TableState): void {
        const medianPointsSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianSubmitted);
        const medianPointsSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianInFirstCorrection);
        const medianScoreSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianRelativeSubmitted);
        const medianScoreSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianRelativeInFirstCorrection);
        const medianGradeSubmitted = this.aggregatedExamResults.medianGradeSubmitted ?? '-';
        const medianGradeSubmittedInFirstCorrection = this.aggregatedExamResults.medianGradeInFirstCorrection ?? '-';
        tableState.medianPointsSubmitted = medianPointsSubmitted;
        tableState.medianPointsTotal = medianPointsSubmitted;
        tableState.medianPointsSubmittedInFirstCorrection = medianPointsSubmittedInFirstCorrection;
        tableState.medianPointsTotalInFirstCorrection = medianPointsSubmittedInFirstCorrection;
        tableState.medianScoreSubmitted = medianScoreSubmitted;
        tableState.medianScoreTotal = medianScoreSubmitted;
        tableState.medianScoreSubmittedInFirstCorrection = medianScoreSubmittedInFirstCorrection;
        tableState.medianScoreTotalInFirstCorrection = medianScoreSubmittedInFirstCorrection;
        tableState.medianGradeSubmitted = medianGradeSubmitted;
        tableState.medianGradeTotal = medianGradeSubmitted;
        tableState.medianGradeSubmittedInFirstCorrection = medianGradeSubmittedInFirstCorrection;
        tableState.medianGradeTotalInFirstCorrection = medianGradeSubmittedInFirstCorrection;
    }

    /**
     * Sets all standard deviations to the table state if only submitted exams should be included in calculation
     * @param tableState the table state that should be updated
     * @private
     */
    private setStandardDeviationForSubmittedFilter(tableState: TableState): void {
        const standardDeviationPointsSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationSubmitted);
        const standardDeviationPointsSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationInFirstCorrection);
        const standardGradeDeviationSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationSubmitted);
        const standardGradeDeviationInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationInFirstCorrection);
        tableState.standardDeviationSubmitted = standardDeviationPointsSubmitted;
        tableState.standardDeviationTotal = standardDeviationPointsSubmitted;
        tableState.standardDeviationSubmittedInFirstCorrection = standardDeviationPointsSubmittedInFirstCorrection;
        tableState.standardDeviationTotalInFirstCorrection = standardDeviationPointsSubmittedInFirstCorrection;
        tableState.standardGradeDeviationSubmitted = standardGradeDeviationSubmitted;
        tableState.standardGradeDeviationTotal = standardGradeDeviationSubmitted;
        tableState.standardGradeDeviationSubmittedInFirstCorrection = standardGradeDeviationInFirstCorrection;
        tableState.standardGradeDeviationTotalInFirstCorrection = standardGradeDeviationInFirstCorrection;
    }

    /**
     * Sets the corresponding values of {@link ExamScoresComponent#aggregatedExamResults} to the table state if no filter is selected
     * @param tableState object containing the values currently displayed by the table
     * @private
     */
    private setValuesForNoFilter(tableState: TableState): void {
        this.setSubmittedValuesForNoFilter(tableState);
        this.setTotalValuesForNoFilter(tableState);
    }

    /**
     * Sets the values for the total row in the table if no filter is selected
     * @param tableState the table state that should be updated
     * @private
     */
    private setTotalValuesForNoFilter(tableState: TableState): void {
        this.setTotalAverageValuesForNoFilter(tableState);
        this.setTotalMedianValuesForNoFilter(tableState);
        this.setTotalStandardDeviationForNoFilter(tableState);
    }

    /**
     * Sets the average values for the total row in the table if no filter is selected
     * @param tableState the table state that should be updated
     * @private
     */
    private setTotalAverageValuesForNoFilter(tableState: TableState): void {
        tableState.averagePointsTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsTotal);
        tableState.averagePointsTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsTotalInFirstCorrection);
        tableState.averageScoreTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsRelativeTotal);
        tableState.averageScoreTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsRelativeTotalInFirstCorrection);
        tableState.averageGradeTotal = this.aggregatedExamResults.meanGradeTotal ?? '-';
        tableState.averageGradeTotalInFirstCorrection = this.aggregatedExamResults.meanGradeTotalInFirstCorrection ?? '-';
    }

    /**
     * Sets the median values for the total row in the table if no filter is selected
     * @param tableState the table state that should be updated
     * @private
     */
    private setTotalMedianValuesForNoFilter(tableState: TableState): void {
        tableState.medianPointsTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianTotal);
        tableState.medianPointsTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianTotalInFirstCorrection);
        tableState.medianScoreTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianRelativeTotal);
        tableState.medianScoreTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianRelativeTotalInFirstCorrection);
        tableState.medianGradeTotal = this.aggregatedExamResults.medianGradeTotal ?? '-';
        tableState.medianGradeTotalInFirstCorrection = this.aggregatedExamResults.medianGradeTotalInFirstCorrection ?? '-';
    }

    /**
     * Sets the standard deviations for the total row in the table if no filter is selected
     * @param tableState the table state that should be updated
     * @private
     */
    private setTotalStandardDeviationForNoFilter(tableState: TableState): void {
        tableState.standardDeviationTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationTotal);
        tableState.standardDeviationTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationTotalInFirstCorrection);
        tableState.standardGradeDeviationTotal = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationTotal);
        tableState.standardGradeDeviationTotalInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationTotalInFirstCorrection);
    }

    /**
     * Sets the values for the submitted row in the table if no filter is selected
     * @param tableState the table state that should be updated
     * @private
     */
    private setSubmittedValuesForNoFilter(tableState: TableState): void {
        this.setSubmittedAverageValuesForNoFilter(tableState);
        this.setSubmittedMedianValuesForNoFilter(tableState);
        this.setSubmittedStandardDeviationsForNoFilter(tableState);
    }

    /**
     * Sets the average values for the submitted row in the table if no filter is selected
     * @param tableState the table state that should be updated
     * @private
     */
    private setSubmittedAverageValuesForNoFilter(tableState: TableState): void {
        tableState.averagePointsSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsSubmitted);
        tableState.averagePointsSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsInFirstCorrection);
        tableState.averageScoreSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsRelativeSubmitted);
        tableState.averageScoreSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.meanPointsRelativeInFirstCorrection);
        tableState.averageGradeSubmitted = this.aggregatedExamResults.meanGradeSubmitted ?? '-';
        tableState.averageGradeSubmittedInFirstCorrection = this.aggregatedExamResults.meanGradeInFirstCorrection ?? '-';
    }

    /**
     * Sets the median for the submitted row in the table if no filter is selected
     * @param tableState the table state that should be updated
     * @private
     */
    private setSubmittedMedianValuesForNoFilter(tableState: TableState): void {
        tableState.medianPointsSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianSubmitted);
        tableState.medianPointsSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianInFirstCorrection);
        tableState.medianScoreSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianRelativeSubmitted);
        tableState.medianScoreSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.medianRelativeInFirstCorrection);
        tableState.medianGradeSubmitted = this.aggregatedExamResults.medianGradeSubmitted ?? '-';
        tableState.medianGradeSubmittedInFirstCorrection = this.aggregatedExamResults.medianGradeInFirstCorrection ?? '-';
    }

    /**
     * Sets the standard deviations for the submitted row in the table if no filter is selected
     * @param tableState the table state that should be updated
     * @private
     */
    private setSubmittedStandardDeviationsForNoFilter(tableState: TableState): void {
        tableState.standardDeviationSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationSubmitted);
        tableState.standardDeviationSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardDeviationInFirstCorrection);
        tableState.standardGradeDeviationSubmitted = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationSubmitted);
        tableState.standardGradeDeviationSubmittedInFirstCorrection = this.roundAndLocalizeStatisticalValue(this.aggregatedExamResults.standardGradeDeviationInFirstCorrection);
    }

    /**
     * Wrapper method that handles null or undefined values for statistical numbers and replaces it with '-' string.
     * If the passed value is not null or undefined, the rounded and localized string is returned
     * @param value the value that should be rounded and localized
     * @private
     */
    private roundAndLocalizeStatisticalValue(value: number | undefined): string {
        if (value === null || value === undefined) {
            return '-';
        }
        return this.roundAndPerformLocalConversion(value);
    }
}
