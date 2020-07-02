import { Component, OnInit } from '@angular/core';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { SortService } from 'app/shared/service/sort.service';
import { ExportToCsv } from 'export-to-csv';
import { ExamScoreDTO, ExerciseGroup, StudentResult } from 'app/exam/exam-scores/ExamScoreDTOs';

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

    constructor(private route: ActivatedRoute, private examService: ExamManagementService, private sortService: SortService) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.examService.getExamScore(params['courseId'], params['examId']).subscribe((examResponse) => {
                this.examScoreDTO = examResponse.body!;
                if (this.examScoreDTO) {
                    this.studentResults = this.examScoreDTO.studentResults;
                    this.exerciseGroups = this.examScoreDTO.exerciseGroups;
                }
            });
        });
    }

    sortRows() {
        this.sortService.sortByProperty(this.examScoreDTO.studentResults, this.predicate, this.reverse);
    }

    exportToCsv() {
        const headers = ['Name', 'Login', 'E-Mail', 'Registration Number'];
        this.exerciseGroups.forEach((exerciseGroup) => {
            headers.push(exerciseGroup.title + ' Assigned Exercise');
            headers.push(exerciseGroup.title + ' Achieved Points');
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
            showTitle: true,
            title: this.examScoreDTO.title,
            useTextFile: false,
            useBom: true,
            headers,
        };

        const csvExporter = new ExportToCsv(options);

        csvExporter.generateCsv(data);
    }

    convertToCSVRow(studentResult: StudentResult) {
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
                csvRow[exerciseGroup.title + 'AchievedPoints'] = exerciseResult.achievedPoints ? exerciseResult.achievedPoints : '';
            } else {
                csvRow[exerciseGroup.title + 'AssignedExercise'] = '';
                csvRow[exerciseGroup.title + 'AchievedPoints'] = '';
            }
        });

        csvRow.overAllPoints = studentResult.overallPointsAchieved ? studentResult.overallPointsAchieved : '';
        csvRow.overAllScore = studentResult.overallScoreAchieved ? studentResult.overallScoreAchieved : '';

        return csvRow;
    }
}
