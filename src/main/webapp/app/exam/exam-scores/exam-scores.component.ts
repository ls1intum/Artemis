import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { forkJoin, Subscription, of } from 'rxjs';
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
    ExerciseInfo,
    StudentResult,
} from 'app/exam/exam-scores/exam-score-dtos.model';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';
import { round } from 'app/shared/util/utils';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import * as SimpleStatistics from 'simple-statistics';
import * as Chart from 'chart.js';
import { ChartDataSets, ChartOptions, ChartType, LinearTickOptions } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { TranslateService } from '@ngx-translate/core';
import { ParticipantScoresService, ScoresDTO } from 'app/shared/participant-scores/participant-scores.service';
import * as Sentry from '@sentry/browser';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exam-scores',
    templateUrl: './exam-scores.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrls: ['./exam-scores.component.scss'],
})
export class ExamScoresComponent implements OnInit, OnDestroy {
    public examScoreDTO: ExamScoreDTO;
    public exerciseGroups: ExerciseGroup[];
    public studentResults: StudentResult[];

    // Data structures for calculated statistics
    // TODO: Cache already calculated filter dependent statistics
    public aggregatedExamResults: AggregatedExamResult;
    public aggregatedExerciseGroupResults: AggregatedExerciseGroupResult[];
    public binWidth = 5;
    public histogramData: number[] = Array(100 / this.binWidth).fill(0);
    public noOfExamsFiltered: number;

    // exam score dtos
    studentIdToExamScoreDTOs: Map<number, ScoresDTO> = new Map<number, ScoresDTO>();

    public predicate = 'id';
    public reverse = false;
    public isLoading = true;
    public filterForSubmittedExams = false;
    public filterForNonEmptySubmissions = false;

    // Histogram related properties
    public barChartOptions: ChartOptions = {};
    public barChartLabels: Label[] = [];
    public barChartType: ChartType = 'bar';
    public barChartLegend = true;
    public barChartData: ChartDataSets[] = [];

    gradingScaleExists = false;
    gradingScale?: GradingScale;
    isBonus?: boolean;
    hasSecondCorrectionAndStarted: boolean;
    hasNumericGrades: boolean;

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    private languageChangeSubscription?: Subscription;
    constructor(
        private route: ActivatedRoute,
        private examService: ExamManagementService,
        private sortService: SortService,
        private jhiAlertService: JhiAlertService,
        private changeDetector: ChangeDetectorRef,
        private languageHelper: JhiLanguageHelper,
        private localeConversionService: LocaleConversionService,
        private translateService: TranslateService,
        private participantScoresService: ParticipantScoresService,
        private gradingSystemService: GradingSystemService,
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

            forkJoin([getExamScoresObservable, findExamScoresObservable, gradingScaleObservable]).subscribe(
                ([getExamScoresResponse, findExamScoresResponse, gradingScaleResponse]) => {
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
                    }
                    this.isLoading = false;
                    this.createChart();
                    this.changeDetector.detectChanges();
                    this.compareNewExamScoresCalculationWithOldCalculation(findExamScoresResponse.body!);
                },
                (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
            );
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
        this.changeDetector.detectChanges();
    }

    toggleFilterForNonEmptySubmission() {
        this.filterForNonEmptySubmissions = !this.filterForNonEmptySubmissions;
        this.calculateFilterDependentStatistics();
        this.changeDetector.detectChanges();
    }

    private calculateTickMax() {
        const max = Math.max(...this.histogramData);
        return Math.ceil((max + 1) / 10) * 10 + 20;
    }

    private createChart() {
        const labels: string[] = [];
        if (this.gradingScaleExists) {
            this.gradingScale!.gradeSteps.forEach((gradeStep, i) => {
                labels[i] = gradeStep.lowerBoundInclusive || i === 0 ? '[' : '(';
                labels[i] += `${gradeStep.lowerBoundPercentage},${gradeStep.upperBoundPercentage}`;
                labels[i] += gradeStep.upperBoundInclusive || i === 100 ? ']' : ')';
                labels[i] += ` {${gradeStep.gradeName}}`;
            });
        } else {
            for (let i = 0; i < this.histogramData.length; i++) {
                labels[i] = `[${i * this.binWidth},${(i + 1) * this.binWidth}`;
                labels[i] += i === this.histogramData.length - 1 ? ']' : ')';
            }
        }
        this.barChartLabels = labels;

        const component = this;

        this.barChartOptions = {
            responsive: true,
            maintainAspectRatio: false,
            legend: {
                align: 'start',
                position: 'bottom',
            },
            scales: {
                yAxes: [
                    {
                        scaleLabel: {
                            display: true,
                            labelString: this.translateService.instant('artemisApp.examScores.yAxes'),
                        },
                        ticks: {
                            maxTicksLimit: 11,
                            beginAtZero: true,
                            precision: 0,
                            min: 0,
                            max: this.calculateTickMax(),
                        } as LinearTickOptions,
                    },
                ],
                xAxes: [
                    {
                        scaleLabel: {
                            display: true,
                            labelString: this.translateService.instant('artemisApp.examScores.xAxes'),
                        },
                    },
                ],
            },
            hover: {
                animationDuration: 0,
            },
            animation: {
                duration: 1,
                onComplete() {
                    const chartInstance = this.chart,
                        ctx = chartInstance.ctx;

                    ctx.font = Chart.helpers.fontString(Chart.defaults.global.defaultFontSize, Chart.defaults.global.defaultFontStyle, Chart.defaults.global.defaultFontFamily);
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'bottom';

                    this.data.datasets.forEach(function (dataset: DataSet, j: number) {
                        const meta = chartInstance.controller.getDatasetMeta(j);
                        meta.data.forEach(function (bar: any, index: number) {
                            const data = dataset.data[index];
                            ctx.fillText(data, bar._model.x, bar._model.y - 20);
                            ctx.fillText(`(${component.roundAndPerformLocalConversion((data * 100) / component.noOfExamsFiltered, 2, 2)}%)`, bar._model.x, bar._model.y - 5);
                        });
                    });
                },
            },
        };

        this.barChartData = [
            {
                label: '# of students',
                data: this.histogramData,
                backgroundColor: 'rgba(0,0,0,0.5)',
            },
        ];
    }

    /**
     * Find the grade step index for the corresponding grade step to the given percentage
     * @param percentage the percentage which will be mapped to a grade step
     */
    findGradeStepIndex(percentage: number): number {
        let index = 0;
        this.gradingScale!.gradeSteps.forEach((gradeStep, i) => {
            if (this.gradingSystemService.matchGradePercentage(gradeStep, percentage)) {
                index = i;
            }
        });
        return index;
    }

    /**
     * Calculates filter dependent exam statistics. Must be triggered if filter settings change.
     * 1. The average points and number of participants for each exercise group and exercise
     * 2. Distribution of scores
     */
    private calculateFilterDependentStatistics() {
        if (this.gradingScaleExists) {
            this.histogramData = Array(this.gradingScale!.gradeSteps.length);
        }
        this.histogramData.fill(0);

        // Create data structures holding the statistics for all exercise groups and exercises
        const groupIdToGroupResults = new Map<number, AggregatedExerciseGroupResult>();
        for (const exerciseGroup of this.exerciseGroups) {
            const groupResult = new AggregatedExerciseGroupResult(exerciseGroup.id, exerciseGroup.title, exerciseGroup.maxPoints, exerciseGroup.numberOfParticipants);
            // We initialize the data structure for exercises here as it can happen that no student was assigned to an exercise
            exerciseGroup.containedExercises.forEach((exerciseInfo) => {
                const type = ExamScoresComponent.declareExerciseType(exerciseInfo);
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
            // Update histogram data structure
            let histogramIndex: number;
            if (this.gradingScaleExists) {
                histogramIndex = this.findGradeStepIndex(studentResult.overallScoreAchieved ?? 0);
            } else {
                histogramIndex = Math.floor(studentResult.overallScoreAchieved! / this.binWidth);
                if (histogramIndex >= 100 / this.binWidth) {
                    // This happens, for 100%, if the exam total points were not set correctly or bonus points were given
                    histogramIndex = 100 / this.binWidth - 1;
                }
            }
            this.histogramData[histogramIndex]++;
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
        this.noOfExamsFiltered = SimpleStatistics.sum(this.histogramData);
        // Calculate exercise group and exercise statistics
        const exerciseGroupResults = Array.from(groupIdToGroupResults.values());
        this.calculateExerciseGroupStatistics(exerciseGroupResults);

        if (this.chart) {
            this.chart.options!.scales!.yAxes![0]!.ticks!.max = this.calculateTickMax();
            this.barChartData[0].data = this.histogramData;
            this.chart.update(0);
        }
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
            examStatistics.meanPointsPassed = SimpleStatistics.mean(studentPointsPassed);
            examStatistics.medianPassed = SimpleStatistics.median(studentPointsPassed);
            examStatistics.standardDeviationPassed = SimpleStatistics.standardDeviation(studentPointsPassed);
            examStatistics.noOfExamsFilteredForPassed = studentPointsPassed.length;
            if (this.examScoreDTO.maxPoints) {
                examStatistics.meanPointsRelativePassed = (examStatistics.meanPointsPassed / this.examScoreDTO.maxPoints) * 100;
                examStatistics.medianRelativePassed = (examStatistics.medianPassed / this.examScoreDTO.maxPoints) * 100;
                examStatistics.meanGradePassed = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, examStatistics.meanPointsRelativePassed)!.gradeName;
                examStatistics.medianGradePassed = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, examStatistics.medianRelativePassed)!.gradeName;
                examStatistics.standardGradeDeviationPassed = this.hasNumericGrades ? SimpleStatistics.standardDeviation(studentGradesPassed) : undefined;
            }
            // Calculate statistics for the first assessments of passed exams if second correction exists
            if (this.hasSecondCorrectionAndStarted) {
                examStatistics.meanPointsPassedInFirstCorrection = SimpleStatistics.mean(studentPointsPassedInFirstCorrectionRound);
                examStatistics.medianPassedInFirstCorrection = SimpleStatistics.median(studentPointsPassedInFirstCorrectionRound);
                examStatistics.standardDeviationPassedInFirstCorrection = SimpleStatistics.standardDeviation(studentPointsPassedInFirstCorrectionRound);
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
                    examStatistics.standardGradeDeviationPassedInFirstCorrection = this.hasNumericGrades
                        ? SimpleStatistics.standardDeviation(studentGradesPassedInFirstCorrectionRound)
                        : undefined;
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
            examStatistics.meanPoints = SimpleStatistics.mean(studentPointsSubmitted);
            examStatistics.median = SimpleStatistics.median(studentPointsSubmitted);
            examStatistics.standardDeviation = SimpleStatistics.standardDeviation(studentPointsSubmitted);
            examStatistics.noOfExamsFiltered = studentPointsSubmitted.length;
            if (this.examScoreDTO.maxPoints) {
                examStatistics.meanPointsRelative = (examStatistics.meanPoints / this.examScoreDTO.maxPoints) * 100;
                examStatistics.medianRelative = (examStatistics.median / this.examScoreDTO.maxPoints) * 100;
                if (this.gradingScaleExists) {
                    examStatistics.meanGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, examStatistics.meanPointsRelative)!.gradeName;
                    examStatistics.medianGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale!.gradeSteps, examStatistics.medianRelative)!.gradeName;
                    examStatistics.standardGradeDeviation = this.hasNumericGrades ? SimpleStatistics.standardDeviation(studentGradesSubmitted) : undefined;
                }
            }
            // Calculate statistics for the first assessments of submitted exams if second correction exists
            if (this.hasSecondCorrectionAndStarted) {
                examStatistics.meanPointsInFirstCorrection = SimpleStatistics.mean(studentPointsSubmittedInFirstCorrectionRound);
                examStatistics.medianInFirstCorrection = SimpleStatistics.median(studentPointsSubmittedInFirstCorrectionRound);
                examStatistics.standardDeviationInFirstCorrection = SimpleStatistics.standardDeviation(studentPointsSubmittedInFirstCorrectionRound);
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
                            ? SimpleStatistics.standardDeviation(studentGradesSubmittedInFirstCorrectionRound)
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
            examStatistics.meanPointsTotal = SimpleStatistics.mean(studentPointsTotal);
            examStatistics.medianTotal = SimpleStatistics.median(studentPointsTotal);
            examStatistics.standardDeviationTotal = SimpleStatistics.standardDeviation(studentPointsTotal);
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
                    examStatistics.standardGradeDeviationTotal = this.hasNumericGrades ? SimpleStatistics.standardDeviation(studentGradesTotal) : undefined;
                }
            }
            // Calculate total statistics if second correction exists
            if (this.hasSecondCorrectionAndStarted) {
                examStatistics.meanPointsTotalInFirstCorrection = SimpleStatistics.mean(studentPointsTotalInFirstCorrectionRound);
                examStatistics.medianTotalInFirstCorrection = SimpleStatistics.median(studentPointsTotalInFirstCorrectionRound);
                examStatistics.standardDeviationTotalInFirstCorrection = SimpleStatistics.standardDeviation(studentPointsTotalInFirstCorrectionRound);
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
                            ? SimpleStatistics.standardDeviation(studentGradesTotalInFirstCorrectionRound)
                            : undefined;
                    }
                }
            }
        }
        return examStatistics;
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
            groupResult.exerciseResults.forEach((exResult) => {
                if (exResult.noOfParticipantsWithFilter) {
                    exResult.averagePoints = exResult.totalPoints / exResult.noOfParticipantsWithFilter;
                    exResult.averagePercentage = (exResult.averagePoints / exResult.maxPoints) * 100;
                }
            });
        }
        this.aggregatedExerciseGroupResults = exerciseGroupResults;
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
     * Wrapper for round utility function so it can be used in the template.
     * @param value
     * @param exp
     */
    round(value: any, exp: number) {
        return round(value, exp);
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
                    exerciseResult.achievedPoints == undefined ? '' : this.localeConversionService.toLocaleString(round(exerciseResult.achievedPoints, 1));
                csvRow[exerciseGroup.title + ' Achieved Score (%)'] =
                    exerciseResult.achievedScore == undefined ? '' : this.localeConversionService.toLocaleString(round(exerciseResult.achievedScore, 2), 2);
            } else {
                csvRow[exerciseGroup.title + ' Assigned Exercise'] = '';
                csvRow[exerciseGroup.title + ' Achieved Points'] = '';
                csvRow[exerciseGroup.title + ' Achieved Score (%)'] = '';
            }
        });

        csvRow.overAllPoints = studentResult.overallPointsAchieved == undefined ? '' : this.localeConversionService.toLocaleString(round(studentResult.overallPointsAchieved, 1));
        csvRow.overAllScore = studentResult.overallScoreAchieved == undefined ? '' : this.localeConversionService.toLocaleString(round(studentResult.overallScoreAchieved, 2), 2);
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

    toLocaleString(points: number) {
        return this.localeConversionService.toLocaleString(points);
    }

    roundAndPerformLocalConversion(points: number | undefined, exp: number, fractions = 1) {
        return this.localeConversionService.toLocaleString(round(points, exp), fractions);
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
        let noOfScoreDifferencesFound = 0;
        let noOfPointDifferencesFound = 0;
        let noOfComparisons = 0;
        for (const studentResult of this.studentResults) {
            const overAllPoints = round(studentResult.overallPointsAchieved, 1);
            const overallScore = round(studentResult.overallScoreAchieved, 1);

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
                noOfComparisons += 1;
                examScoreDTO.scoreAchieved = round(examScoreDTO.scoreAchieved, 1);
                examScoreDTO.pointsAchieved = round(examScoreDTO.pointsAchieved, 1);

                if (Math.abs(examScoreDTO.pointsAchieved - regularCalculation.pointsAchieved) > 0.1) {
                    const errorMessage = `Different exam points in new calculation. Regular Calculation: ${JSON.stringify(regularCalculation)}. New Calculation: ${JSON.stringify(
                        examScoreDTO,
                    )}`;
                    noOfPointDifferencesFound += 1;
                    this.logErrorOnSentry(errorMessage);
                }
                if (Math.abs(examScoreDTO.scoreAchieved - regularCalculation.scoreAchieved) > 0.1) {
                    const errorMessage = `Different exam score in new calculation. Regular Calculation: ${JSON.stringify(regularCalculation)}. New Calculation : ${JSON.stringify(
                        examScoreDTO,
                    )}`;
                    noOfScoreDifferencesFound += 1;
                    this.logErrorOnSentry(errorMessage);
                }
            }
        }
        console.log(`Performed ${noOfComparisons} comparisons between old and new calculation method.`);
        console.log(`Found ${noOfPointDifferencesFound} point differences between old and new calculation method.`);
        console.log(`Found ${noOfScoreDifferencesFound} point differences between old and new calculation method.`);
    }

    logErrorOnSentry(errorMessage: string) {
        console.log(errorMessage);
        Sentry.captureException(new Error(errorMessage));
    }

    private static declareExerciseType(exerciseInfo: ExerciseInfo) {
        switch (exerciseInfo.exerciseType) {
            case 'TextExercise':
                return ExerciseType.TEXT;
            case 'ModelingExercise':
                return ExerciseType.MODELING;
            case 'ProgrammingExercise':
                return ExerciseType.PROGRAMMING;
            case 'FileUploadExercise':
                return ExerciseType.FILE_UPLOAD;
            case 'QuizExercise':
                return ExerciseType.QUIZ;
        }
    }
}
