import { Component, OnInit, OnDestroy, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { Subscription } from 'rxjs';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { SortService } from 'app/shared/service/sort.service';
import { ExportToCsv } from 'export-to-csv';
import { AggregatedExerciseGroupResult, AggregatedExerciseResult, ExamScoreDTO, ExerciseGroup, StudentResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/alert/alert.service';
import { round } from 'app/shared/util/utils';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

@Component({
    selector: 'jhi-exam-scores',
    templateUrl: './exam-scores.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styles: [],
})
export class ExamScoresComponent implements OnInit, OnDestroy {
    public examScoreDTO: ExamScoreDTO;
    public exerciseGroups: ExerciseGroup[];
    public studentResults: StudentResult[];

    public aggregatedExerciseGroupResults: AggregatedExerciseGroupResult[];

    public predicate = 'id';
    public reverse = false;
    public isLoading = true;
    public filterForSubmittedExams = false;
    public filterForNonEmptySubmissions = false;

    private languageChangeSubscription: Subscription | null;

    constructor(
        private route: ActivatedRoute,
        private examService: ExamManagementService,
        private sortService: SortService,
        private jhiAlertService: AlertService,
        private changeDetector: ChangeDetectorRef,
        private languageHelper: JhiLanguageHelper,
        private localeConversionService: LocaleConversionService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.examService.getExamScores(params['courseId'], params['examId']).subscribe(
                (examResponse) => {
                    this.examScoreDTO = examResponse.body!;
                    if (this.examScoreDTO) {
                        this.studentResults = this.examScoreDTO.studentResults;
                        this.exerciseGroups = this.examScoreDTO.exerciseGroups;
                        this.calculateAveragePoints();
                    }
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
        this.calculateAveragePoints();
        this.changeDetector.detectChanges();
    }

    toggleFilterForNonEmptySubmission() {
        this.filterForNonEmptySubmissions = !this.filterForNonEmptySubmissions;
        this.calculateAveragePoints();
        this.changeDetector.detectChanges();
    }

    calculateAveragePoints() {
        const groupIdToGroupResults = new Map<number, AggregatedExerciseGroupResult>();
        // Create data structures for all exercise groups
        for (const exerciseGroup of this.exerciseGroups) {
            const groupResult = new AggregatedExerciseGroupResult(exerciseGroup.exerciseGroupId, exerciseGroup.title, exerciseGroup.maxPoints);
            // We initialize the data structure for exercises here as it can happen that no student was assigned to an exercise
            exerciseGroup.containedExercises.forEach((exerciseInfo) => {
                groupResult.exerciseResults.push(new AggregatedExerciseResult(exerciseInfo.exerciseId, exerciseInfo.title));
            });
            groupIdToGroupResults.set(exerciseGroup.exerciseGroupId, groupResult);
        }

        // Calculate the total points and number of participants when filters apply for each exercise group and exercise
        for (const studentResult of this.studentResults) {
            // Do not take un-submitted exams into account if the option was set
            if (!studentResult.submitted && this.filterForSubmittedExams) {
                continue;
            }
            for (const [exGroupId, studentExerciseResult] of studentResult.exerciseGroupIdToExerciseResult.entries()) {
                // Ignore exercise results with only empty submission if the option was set
                if (this.filterForNonEmptySubmissions && !studentExerciseResult.hasNonEmptySubmission) {
                    continue;
                }
                // Update the exerciseGroup statistic
                const exGroupResult = groupIdToGroupResults.get(exGroupId);
                if (!exGroupResult) {
                    // This should never been thrown. Indicates that the information in the ExamScoresDTO is inconsistent
                    throw new Error('TODO');
                }
                exGroupResult.noOfParticipantsWithFilter += 1;
                exGroupResult.totalPoints += studentExerciseResult.achievedPoints;

                // Update the specific exercise statistic
                const exerciseResult = exGroupResult.exerciseResults.find((exResult) => exResult.exerciseId === studentExerciseResult.exerciseId);
                if (!exerciseResult) {
                    // This should never been thrown. Indicates that the information in the ExamScoresDTO is inconsistent
                    throw new Error('TODO');
                } else {
                    exerciseResult.noOfParticipantsWithFilter += 1;
                    exerciseResult.totalPoints += studentExerciseResult.achievedPoints;
                }
            }
        }
        // Calculate average scores for exercise groups
        const exerciseGroupResults = Array.from(groupIdToGroupResults.values());
        for (const groupResult of exerciseGroupResults) {
            if (groupResult.noOfParticipantsWithFilter) {
                groupResult.averagePoints = groupResult.totalPoints / groupResult.noOfParticipantsWithFilter;
            }
            // Calculate average scores for exercises
            groupResult.exerciseResults.forEach((exResult) => {
                if (exResult.noOfParticipantsWithFilter) {
                    exResult.averagePoints = exResult.totalPoints / exResult.noOfParticipantsWithFilter;
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

        const data = this.studentResults.map((studentResult) => {
            return this.convertToCSVRow(studentResult);
        });

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

        csvExporter.generateCsv(data);
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
            const exerciseResult = studentResult.exerciseGroupIdToExerciseResult[exerciseGroup.exerciseGroupId];
            if (exerciseResult) {
                csvRow[exerciseGroup.title + 'AssignedExercise'] = exerciseResult.title ? exerciseResult.title : '';
                csvRow[exerciseGroup.title + 'AchievedPoints'] =
                    typeof exerciseResult.achievedPoints === 'undefined' || exerciseResult.achievedPoints === null
                        ? ''
                        : this.localeConversionService.toLocaleString(round(exerciseResult.achievedPoints, 1));
                csvRow[exerciseGroup.title + 'AchievedScore(%)'] =
                    typeof exerciseResult.achievedScore === 'undefined' || exerciseResult.achievedScore === null
                        ? ''
                        : this.localeConversionService.toLocaleString(round(exerciseResult.achievedScore, 2), 2);
            } else {
                csvRow[exerciseGroup.title + 'AssignedExercise'] = '';
                csvRow[exerciseGroup.title + 'AchievedPoints'] = '';
                csvRow[exerciseGroup.title + 'AchievedScore(%)'] = '';
            }
        });

        csvRow.overAllPoints =
            typeof studentResult.overallPointsAchieved === 'undefined' || studentResult.overallPointsAchieved === null
                ? ''
                : this.localeConversionService.toLocaleString(round(studentResult.overallPointsAchieved, 1));
        csvRow.overAllScore =
            typeof studentResult.overallScoreAchieved === 'undefined' || studentResult.overallScoreAchieved === null
                ? ''
                : this.localeConversionService.toLocaleString(round(studentResult.overallScoreAchieved, 2), 2);
        csvRow.submitted = studentResult.submitted ? 'yes' : 'no';
        return csvRow;
    }

    toLocaleString(points: number) {
        return this.localeConversionService.toLocaleString(points);
    }

    roundAndPerformLocalConversion(points: number, exp: number, fractions = 1) {
        return this.localeConversionService.toLocaleString(round(points, exp), fractions);
    }
}
