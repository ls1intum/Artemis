import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ActivatedRoute, Router } from '@angular/router';
import { SortService } from 'app/shared/service/sort.service';
import { ExportToCsv } from 'export-to-csv';
import {
    AggregatedExamResult,
    AggregatedExerciseGroupResult,
    AggregatedExerciseResult,
    ExamScoreDTO,
    ExerciseGroup,
    StudentResult,
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
import { faCheckCircle, faDownload, faSort, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { GradingInterval } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';
import { ParticipantScoresDistributionService } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.service';

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
    lastCalculatedMedianType: MedianType;
    highlightedValue: number | undefined;

    showOverallMedian: boolean; // Indicates whether the median of all exams is currently highlighted
    showOverallMedianCheckbox = true; // Indicates whether the checkbox for toggling the highlighting of overallChartMedian is currently visible to the user
    overallChartMedian: number; // This value can vary as it depends on if the user only includes submitted exams or not
    overallChartMedianType: MedianType; // We need to distinguish the different overall medians for the toggling
    showPassedMedian: boolean; // Same as above for the median of all passed exams
    showPassedMedianCheckbox: boolean; // Same as above for the checkbox corresponding to passedMedian

    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly medianType = MedianType;

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
        private router: Router,
        private accountService: AccountService,
        private participantScoresDistributionService: ParticipantScoresDistributionService,
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
                        this.gradingScale!.gradeSteps = this.gradingSystemService.sortGradeSteps(this.gradingScale!.gradeSteps);
                        this.hasNumericGrades = !this.gradingScale!.gradeSteps.some((step) => isNaN(Number(step.gradeName)));
                    }
                    // Only try to calculate statistics if the exam has exercise groups and student results
                    if (this.studentResults && this.exerciseGroups) {
                        // Exam statistics must only be calculated once as they are not filter dependent
                        this.calculateExamStatistics();
                        this.calculateFilterDependentStatistics();
                        const medianType = this.gradingScaleExists && !this.isBonus ? MedianType.PASSED : MedianType.OVERALL;
                        // if a grading scale exists and the scoring type is not bonus, per default the median of all passed exams is shown.
                        // We need to set the value for the overall median in order to show it next to the check box
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
        this.changeDetector.detectChanges();
    }

    toggleFilterForNonEmptySubmission() {
        this.filterForNonEmptySubmissions = !this.filterForNonEmptySubmissions;
        this.calculateFilterDependentStatistics();
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
                    // This should never been thrown. Indicates that the information in the ExamScoresDTO is inconsistent
                    throw new Error(`ExerciseGroup with id ${exGroupId} does not exist in this exam!`);
                }
                exGroupResult.noOfParticipantsWithFilter++;
                exGroupResult.totalPoints += studentExerciseResult.achievedPoints!;

                // Update the specific exercise statistic
                const exerciseResult = exGroupResult.exerciseResults.find((exResult) => exResult.exerciseId === studentExerciseResult.exerciseId);
                if (!exerciseResult) {
                    // This should never been thrown. Indicates that the information in the ExamScoresDTO is inconsistent
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
    }

    /**
     * Calculates statistics on exam granularity for passed exams, submitted exams, and for all exams.
     */
    private calculateExamStatistics() {
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

        // Collect student points independent from the filter settings
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
            examStatistics.meanPoints = mean(studentPointsSubmitted);
            examStatistics.median = median(studentPointsSubmitted);
            examStatistics.standardDeviation = standardDeviation(studentPointsSubmitted);
            examStatistics.noOfExamsFiltered = studentPointsSubmitted.length;
            if (this.examScoreDTO.maxPoints) {
                examStatistics.meanPointsRelative = (examStatistics.meanPoints / this.examScoreDTO.maxPoints) * 100;
                examStatistics.medianRelative = (examStatistics.median / this.examScoreDTO.maxPoints) * 100;
                if (this.gradingScaleExists) {
                    examStatistics.meanGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, examStatistics.meanPointsRelative)!.gradeName;
                    examStatistics.medianGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, examStatistics.medianRelative)!.gradeName;
                    examStatistics.standardGradeDeviation = this.hasNumericGrades ? standardDeviation(studentGradesSubmitted) : undefined;
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

    exportToCsv() {
        const headers = ['Name', 'Login', 'E-Mail', 'Matriculation Number'];
        this.exerciseGroups.forEach((exerciseGroup) => {
            headers.push(exerciseGroup.title + ' Assigned Exercise');
            headers.push(exerciseGroup.title + ' Achieved Points');
            headers.push(exerciseGroup.title + ' Achieved Score (%)');
        });
        headers.push('Overall Points');
        headers.push('Overall Score (%)');
        if (this.gradingScaleExists) {
            headers.push(this.isBonus ? 'Overall Bonus Points' : 'Overall Grade');
        }
        headers.push('Submitted');
        if (this.gradingScaleExists && !this.isBonus) {
            headers.push('Passed');
        }

        const rows = this.studentResults.map((studentResult) => {
            return this.convertToCSVRow(studentResult);
        });

        this.exportAsCsv(rows, headers);
    }

    exportAsCsv(rows: any[], headers: string[]) {
        const options = {
            fieldSeparator: ';',
            quoteStrings: '"',
            decimalSeparator: 'locale',
            showLabels: true,
            title: this.examScoreDTO.title,
            filename: this.examScoreDTO.title + 'Results',
            useTextFile: false,
            useBom: true,
            headers,
        };

        const csvExporter = new ExportToCsv(options);
        csvExporter.generateCsv(rows);
    }

    /**
     * Localizes a number, e.g. switching the decimal separator
     */
    localize(numberToLocalize: number): string {
        return this.localeConversionService.toLocaleString(numberToLocalize, this.course!.accuracyOfScores!);
    }

    private convertToCSVRow(studentResult: StudentResult) {
        const csvRow: any = {
            name: studentResult.name ? studentResult.name : '',
            login: studentResult.login ? studentResult.login : '',
            eMail: studentResult.eMail ? studentResult.eMail : '',
            registrationNumber: studentResult.registrationNumber ? studentResult.registrationNumber : '',
        };

        this.exerciseGroups.forEach((exerciseGroup) => {
            const exerciseResult = studentResult.exerciseGroupIdToExerciseResult?.[exerciseGroup.id];
            if (exerciseResult) {
                csvRow[exerciseGroup.title + ' Assigned Exercise'] = exerciseResult.title ? exerciseResult.title : '';
                csvRow[exerciseGroup.title + ' Achieved Points'] =
                    exerciseResult.achievedPoints == undefined ? '' : this.localize(roundValueSpecifiedByCourseSettings(exerciseResult.achievedPoints, this.course));
                csvRow[exerciseGroup.title + ' Achieved Score (%)'] =
                    exerciseResult.achievedScore == undefined ? '' : this.localize(roundValueSpecifiedByCourseSettings(exerciseResult.achievedScore, this.course));
            } else {
                csvRow[exerciseGroup.title + ' Assigned Exercise'] = '';
                csvRow[exerciseGroup.title + ' Achieved Points'] = '';
                csvRow[exerciseGroup.title + ' Achieved Score (%)'] = '';
            }
        });

        csvRow.overAllPoints =
            studentResult.overallPointsAchieved == undefined ? '' : this.localize(roundValueSpecifiedByCourseSettings(studentResult.overallPointsAchieved, this.course));
        csvRow.overAllScore =
            studentResult.overallScoreAchieved == undefined ? '' : this.localize(roundValueSpecifiedByCourseSettings(studentResult.overallScoreAchieved, this.course));
        if (this.gradingScaleExists) {
            if (this.isBonus) {
                csvRow['Overall Bonus Points'] = studentResult.overallGrade;
            } else {
                csvRow['Overall Grade'] = studentResult.overallGrade;
            }
        }
        csvRow.submitted = studentResult.submitted ? 'yes' : 'no';
        if (this.gradingScaleExists && !this.isBonus) {
            csvRow['Passed'] = studentResult.hasPassed ? 'yes' : 'no';
        }
        return csvRow;
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
            this.router.navigate(['course-management', this.course!.id, 'exams', this.examScoreDTO.examId, 'participant-scores']);
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
            chartMedian = passedMedian ? roundValueSpecifiedByCourseSettings(passedMedian, this.course) : undefined;
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
            const submittedMedian = this.aggregatedExamResults.medianRelative;
            this.overallChartMedian = submittedMedian ? roundValueSpecifiedByCourseSettings(submittedMedian, this.course) : 0;
        }
        this.overallChartMedianType = medianType;
    }

    /**
     * Sets the visibility of checkboxes depending on the emitter output of {@link ParticipantScoresDistribution#emptyBars}
     * @param intervals the bars that are empty in the distribution
     */
    setVisibilityOfCheckBoxes(intervals: GradingInterval[]): void {
        if (!this.aggregatedExamResults.medianRelativePassed) {
            this.showPassedMedianCheckbox = false;
        } else {
            this.showPassedMedianCheckbox = !this.participantScoresDistributionService.isContainingIntervalPresent(this.aggregatedExamResults.medianRelativePassed, intervals);
        }
        this.showOverallMedianCheckbox = !this.participantScoresDistributionService.isContainingIntervalPresent(this.overallChartMedian, intervals);
    }
}
