import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { SortService } from 'app/shared/service/sort.service';
import { ExportToCsv } from 'export-to-csv';
import { ExamScoreDTO, ExerciseGroup, StudentResult } from 'app/exam/exam-scores/exam-score-dtos.model';
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
export class ExamScoresComponent implements OnInit {
    public examScoreDTO: ExamScoreDTO;
    public exerciseGroups: ExerciseGroup[];
    public studentResults: StudentResult[];

    public predicate = 'id';
    public reverse = false;
    public isLoading = true;

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
                    }
                    this.isLoading = false;
                    this.changeDetector.detectChanges();
                },
                (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
            );
        });

        // TODO: do we need to keep the subject and destroy it properly in the lifecycle of the component?
        this.languageHelper.language.subscribe((languageKey: string) => {
            this.changeDetector.detectChanges();
        });
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
            const exerciseResult = studentResult.exerciseGroupIdToExerciseResult[exerciseGroup.id];
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

    getLocaleConversionService() {
        return this.localeConversionService;
    }
}
