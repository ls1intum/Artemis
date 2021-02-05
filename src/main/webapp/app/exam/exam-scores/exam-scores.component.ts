import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';
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
} from 'app/exam/exam-scores/exam-score-dtos.model';
import { HttpErrorResponse } from '@angular/common/http';
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
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.examService.getExamScores(params['courseId'], params['examId']).subscribe(
                (examResponse) => {
                    this.examScoreDTO = examResponse!.body!;
                    if (this.examScoreDTO) {
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
                    // Only try to calculate statistics if the exam has exercise groups and student results
                    if (this.studentResults && this.exerciseGroups) {
                        // Exam statistics must only be calculated once as they are not filter dependent
                        this.calculateExamStatistics();
                        this.calculateFilterDependentStatistics();
                    }
                    this.createChart();
                    this.isLoading = false;
                    this.changeDetector.detectChanges();
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
        let i;
        for (i = 0; i < this.histogramData.length; i++) {
            labels[i] = `[${i * this.binWidth},${(i + 1) * this.binWidth}`;
            labels[i] += i === this.histogramData.length - 1 ? ']' : ')';
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
     * Calculates filter dependent exam statistics. Must be triggered if filter settings change.
     * 1. The average points and number of participants for each exercise group and exercise
     * 2. Distribution of scores
     */
    private calculateFilterDependentStatistics() {
        this.histogramData.fill(0);

        // Create data structures holding the statistics for all exercise groups and exercises
        const groupIdToGroupResults = new Map<number, AggregatedExerciseGroupResult>();
        for (const exerciseGroup of this.exerciseGroups) {
            const groupResult = new AggregatedExerciseGroupResult(exerciseGroup.id, exerciseGroup.title, exerciseGroup.maxPoints, exerciseGroup.numberOfParticipants);
            // We initialize the data structure for exercises here as it can happen that no student was assigned to an exercise
            exerciseGroup.containedExercises.forEach((exerciseInfo) => {
                const exResult = new AggregatedExerciseResult(exerciseInfo.exerciseId, exerciseInfo.title, exerciseInfo.maxPoints, exerciseInfo.numberOfParticipants);
                groupResult.exerciseResults.push(exResult);
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
            let histogramIndex = Math.floor(studentResult.overallScoreAchieved! / this.binWidth);
            if (histogramIndex >= 100 / this.binWidth) {
                // This happens, for 100%, if the exam total points were not set correctly or bonus points were given
                histogramIndex = 100 / this.binWidth - 1;
            }
            this.histogramData[histogramIndex]++;

            for (const [exGroupId, studentExerciseResult] of Object.entries(studentResult.exerciseGroupIdToExerciseResult)) {
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
            this.chart.update(0);
        }
    }

    /**
     * Calculates statistics on exam granularity for submitted exams and for all exams.
     */
    private calculateExamStatistics() {
        const studentPointsSubmitted: number[] = [];
        const studentPointsTotal: number[] = [];

        // Collect student points independent from the filter settings
        for (const studentResult of this.studentResults) {
            studentPointsTotal.push(studentResult.overallPointsAchieved!);
            if (studentResult.submitted) {
                studentPointsSubmitted.push(studentResult.overallPointsAchieved!);
            }
        }

        const examStatistics = new AggregatedExamResult();
        // Calculate statistics for submitted exams
        if (studentPointsSubmitted.length) {
            examStatistics.meanPoints = SimpleStatistics.mean(studentPointsSubmitted);
            examStatistics.median = SimpleStatistics.median(studentPointsSubmitted);
            examStatistics.standardDeviation = SimpleStatistics.standardDeviation(studentPointsSubmitted);
            examStatistics.noOfExamsFiltered = studentPointsSubmitted.length;
            if (this.examScoreDTO.maxPoints) {
                examStatistics.meanPointsRelative = (examStatistics.meanPoints / this.examScoreDTO.maxPoints) * 100;
                examStatistics.medianRelative = (examStatistics.median / this.examScoreDTO.maxPoints) * 100;
            }
        }
        // Calculate total statistics
        if (studentPointsTotal.length) {
            examStatistics.meanPointsTotal = SimpleStatistics.mean(studentPointsTotal);
            examStatistics.medianTotal = SimpleStatistics.median(studentPointsTotal);
            examStatistics.standardDeviationTotal = SimpleStatistics.standardDeviation(studentPointsTotal);
            examStatistics.noOfRegisteredUsers = this.studentResults.length;
            if (this.examScoreDTO.maxPoints) {
                examStatistics.meanPointsRelativeTotal = (examStatistics.meanPointsTotal / this.examScoreDTO.maxPoints) * 100;
                examStatistics.medianRelativeTotal = (examStatistics.medianTotal / this.examScoreDTO.maxPoints) * 100;
            }
        }
        this.aggregatedExamResults = examStatistics;
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
        headers.push('Submitted');

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
            const exerciseResult = studentResult.exerciseGroupIdToExerciseResult[exerciseGroup.id];
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
        csvRow.submitted = studentResult.submitted ? 'yes' : 'no';
        return csvRow;
    }

    toLocaleString(points: number) {
        return this.localeConversionService.toLocaleString(points);
    }

    roundAndPerformLocalConversion(points: number | undefined, exp: number, fractions = 1) {
        return this.localeConversionService.toLocaleString(round(points, exp), fractions);
    }
}
