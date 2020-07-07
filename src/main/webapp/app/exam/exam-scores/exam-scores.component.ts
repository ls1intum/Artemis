import { Component, OnInit } from '@angular/core';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { SortService } from 'app/shared/service/sort.service';
import { ExportToCsv } from 'export-to-csv';
import { ExamScoreDTO, ExerciseGroup, StudentResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/alert/alert.service';

@Component({
    selector: 'jhi-exam-scores',
    templateUrl: './exam-scores.component.html',
    styles: [],
})
export class ExamScoresComponent implements OnInit {
    public examScoreDTO: ExamScoreDTO;
    public exerciseGroups: ExerciseGroup[];
    public studentResults: StudentResult[];

    public predicate = 'id';
    public reverse = false;
    public isLoading = true;

    constructor(private route: ActivatedRoute, private examService: ExamManagementService, private sortService: SortService, private jhiAlertService: AlertService) {}

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
                },
                (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
            );
        });
    }

    /**
     * Helper function to make actually rounding possible
     * @param value
     * @param exp
     */
    round(value: any, exp: number) {
        if (typeof exp === 'undefined' || +exp === 0) {
            return Math.round(value);
        }

        value = +value;
        exp = +exp;

        if (isNaN(value) || !(exp % 1 === 0)) {
            return NaN;
        }

        // Shift
        value = value.toString().split('e');
        value = Math.round(+(value[0] + 'e' + (value[1] ? +value[1] + exp : exp)));

        // Shift back
        value = value.toString().split('e');
        return +(value[0] + 'e' + (value[1] ? +value[1] - exp : -exp));
    }

    sortRows() {
        this.sortService.sortByProperty(this.examScoreDTO.studentResults, this.predicate, this.reverse);
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

        const data = this.studentResults.map((studentResult) => {
            return this.convertToCSVRow(studentResult);
        });

        const options = {
            fieldSeparator: ',',
            quoteStrings: '"',
            decimalSeparator: '.',
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
                    typeof exerciseResult.achievedPoints === 'undefined' || exerciseResult.achievedPoints === null ? '' : this.round(exerciseResult.achievedPoints, 1);
                csvRow[exerciseGroup.title + 'AchievedScore(%)'] =
                    typeof exerciseResult.achievedScore === 'undefined' || exerciseResult.achievedScore === null ? '' : this.round(exerciseResult.achievedScore, 2);
            } else {
                csvRow[exerciseGroup.title + 'AssignedExercise'] = '';
                csvRow[exerciseGroup.title + 'AchievedPoints'] = '';
                csvRow[exerciseGroup.title + 'AchievedScore(%)'] = '';
            }
        });

        csvRow.overAllPoints =
            typeof studentResult.overallPointsAchieved === 'undefined' || studentResult.overallPointsAchieved === null ? '' : this.round(studentResult.overallPointsAchieved, 1);
        csvRow.overAllScore =
            typeof studentResult.overallScoreAchieved === 'undefined' || studentResult.overallScoreAchieved === null ? '' : this.round(studentResult.overallScoreAchieved, 2);
        return csvRow;
    }
}
