import { Component, OnInit, OnDestroy } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { SortService } from 'app/shared/service/sort.service';
import { ExportToCsv } from 'export-to-csv';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

const NAME_KEY = 'Name';
const USERNAME_KEY = 'Username';
const EMAIL_KEY = 'Email';
const REGISTRATION_NUMBER_KEY = 'Registration Number';
const TOTAL_EXAM_POINTS_KEY = 'Total Exam Points';
const TOTAL_EXAM_SCORE_KEY = 'Total Exam Score';
const POINTS_KEY = 'Points';
const SCORE_KEY = 'Score';

@Component({
    selector: 'jhi-exam-scores',
    templateUrl: './exam-scores.component.html',
    styles: [],
})
export class ExamScoresComponent implements OnInit, OnDestroy {
    public examScoreDTO: any;
    public exerciseGroups: any[];
    public rows: any[];

    paramSub: Subscription;
    exam: Exam;
    predicate: string;
    reverse: boolean;

    options: Intl.NumberFormatOptions = { maximumFractionDigits: 1 }; // TODO: allow user to customize
    locale = getLang(); // default value, will be overridden by the current language of Artemis

    constructor(
        private route: ActivatedRoute,
        private examService: ExamManagementService,
        private sortService: SortService,
        private languageService: JhiLanguageService,
        private languageHelper: JhiLanguageHelper,
    ) {
        this.predicate = 'id';
        this.reverse = false;
        this.locale = this.languageService.currentLang;
        this.languageHelper.language.subscribe((languageKey: string) => {
            this.locale = languageKey;
        });
    }

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.examService.find(params['courseId'], params['examId']).subscribe((examResponse) => {
                this.exam = examResponse.body!;
            });
            this.examService.getExamScore(params['courseId'], params['examId']).subscribe((examResponse) => {
                this.examScoreDTO = examResponse.body!;
                if (this.examScoreDTO) {
                    this.rows = this.examScoreDTO.students;
                    this.exerciseGroups = this.examScoreDTO.exerciseGroups;
                }
            });
        });
    }

    calculatePoints(row: any, exerciseGroup: any) {
        return this.round(
            (row.exerciseGroupToExerciseResult[exerciseGroup.id].exerciseAchievedScore * row.exerciseGroupToExerciseResult[exerciseGroup.id].exerciseMaxScore) / 100,
            1,
        );
    }

    private round(value: any, exp: number) {
        // helper function to make actually rounding possible
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

    calculateOverallPoints(row: any) {
        let overall = 0;
        for (let i = 0; i < this.exerciseGroups.length; i++) {
            if (row.exerciseGroupToExerciseResult[this.exerciseGroups[i].id]) {
                overall += this.calculatePoints(row, this.exerciseGroups[i]);
            }
        }
        return overall;
    }

    calculateTotalAverage() {
        let sum = 0;
        for (let i = 0; i < this.rows.length; i++) {
            sum += this.calculateOverallPoints(this.rows[i]);
        }

        return this.round(sum / this.rows.length, 1);
    }

    calculateExerciseGroupAverage(exerciseGroup: any) {
        let sum = 0;
        let hasResult = false;
        for (let i = 0; i < this.rows.length; i++) {
            if (this.rows[i].exerciseGroupToExerciseResult[exerciseGroup.id]) {
                sum += this.calculatePoints(this.rows[i], exerciseGroup);
                hasResult = true;
            }
        }

        if (hasResult) {
            return this.round(sum / this.rows.length, 1);
        } else {
            return '-';
        }
    }

    sortRows() {
        this.sortService.sortByProperty(this.examScoreDTO.results, this.predicate, this.reverse);
    }

    /**
     * Method for exporting the csv with the needed data
     */
    exportResults() {
        if (this.exerciseGroups.length > 0) {
            const rows = [];
            const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
            for (const exerciseGroup of this.exerciseGroups) {
                const exerciseGroupName = exerciseGroup.title;

                // Was macht das?
                // // only add it if there are actually exercises in this type
                // if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                //     keys.push(...this.exerciseTitlesPerType.get(exerciseType)!);
                // }

                keys.push(exerciseGroupName + ' ' + POINTS_KEY);
                keys.push(exerciseGroupName + ' ' + SCORE_KEY);
            }
            keys.push(TOTAL_EXAM_POINTS_KEY, TOTAL_EXAM_SCORE_KEY);

            for (const row of this.rows.values()) {
                const rowData = {};
                rowData[NAME_KEY] = row.name!.trim();
                rowData[USERNAME_KEY] = row.login!.trim();
                rowData[EMAIL_KEY] = row.email!.trim(); // TODO email mit ins ExamScoresDTO
                rowData[REGISTRATION_NUMBER_KEY] = row.registrationNumber ? row.registrationNumber!.trim() : '';

                // TODO adjust for exams
                for (const exerciseGroup of this.exerciseGroups) {
                    // only add it if there are actually exercises in this type
                    if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                        const exerciseGroupName = exerciseGroup.title;
                        const exercisePointsPerType = student.sumPointsPerExerciseType.get(exerciseType)!;
                        let exerciseScoresPerType = 0;
                        if (this.maxNumberOfPointsPerExerciseType.get(exerciseType)! > 0) {
                            exerciseScoresPerType = (student.sumPointsPerExerciseType.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!) * 100;
                        }
                        const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                        const exercisePointValues = student.pointsPerExerciseType.get(exerciseType)!;
                        exerciseTitleKeys.forEach((title, index) => {
                            rowData[title] = this.toLocaleString(exercisePointValues[index]);
                        });
                        rowData[exerciseGroupName + ' ' + POINTS_KEY] = this.toLocaleString(exercisePointsPerType);
                        rowData[exerciseGroupName + ' ' + SCORE_KEY] = this.toLocalePercentageString(exerciseScoresPerType);
                    }
                }

                const overallScore = (this.calculateOverallPoints(row) / this.exam.maxPoints!) * 100;
                rowData[TOTAL_EXAM_POINTS_KEY] = this.toLocaleString(this.calculateOverallPoints(row));
                rowData[TOTAL_EXAM_SCORE_KEY] = this.toLocalePercentageString(overallScore);
                rows.push(rowData);
            }

            rows.push(this.emptyLine('')); // empty row as separator

            // max values
            // TODO adjust for exams
            const rowDataMax = this.emptyLine('Max');
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                    const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                    const exerciseMaxPoints = this.exerciseMaxPointsPerType.get(exerciseType)!;
                    exerciseTitleKeys.forEach((title, index) => {
                        rowDataMax[title] = this.toLocaleString(exerciseMaxPoints[index]);
                    });
                    rowDataMax[exerciseTypeName + ' ' + POINTS_KEY] = this.toLocaleString(this.maxNumberOfPointsPerExerciseType.get(exerciseType)!);
                    rowDataMax[exerciseTypeName + ' ' + SCORE_KEY] = this.toLocalePercentageString(100);
                }
            }
            rowDataMax[TOTAL_EXAM_POINTS_KEY] = this.toLocaleString(this.exam.maxPoints!);
            rowDataMax[TOTAL_EXAM_SCORE_KEY] = this.toLocalePercentageString(100);
            rows.push(rowDataMax);

            // average values
            // TODO adjust for exams
            const rowDataAverage = this.emptyLine('Average');
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                    const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                    const exerciseAveragePoints = this.exerciseAveragePointsPerType.get(exerciseType)!;
                    exerciseTitleKeys.forEach((title, index) => {
                        rowDataAverage[title] = this.toLocaleString(exerciseAveragePoints[index]);
                    });

                    const averageScore = (this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!) * 100;

                    rowDataAverage[exerciseTypeName + ' ' + POINTS_KEY] = this.toLocaleString(this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)!);
                    rowDataAverage[exerciseTypeName + ' ' + SCORE_KEY] = this.toLocalePercentageString(averageScore);
                }
            }

            const averageOverallScore = (this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints) * 100;
            rowDataAverage[TOTAL_EXAM_POINTS_KEY] = this.toLocaleString(this.averageNumberOfOverallPoints);
            rowDataAverage[TOTAL_EXAM_SCORE_KEY] = this.toLocalePercentageString(averageOverallScore);
            rows.push(rowDataAverage);

            // participation
            // TODO adjust for exams
            const rowDataParticipation = this.emptyLine('Number of Participations');
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                    const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                    const exerciseParticipations = this.exerciseParticipationsPerType.get(exerciseType)!;
                    exerciseTitleKeys.forEach((title, index) => {
                        rowDataParticipation[title] = this.toLocaleString(exerciseParticipations[index]);
                    });
                    rowDataParticipation[exerciseTypeName + ' ' + POINTS_KEY] = '';
                    rowDataParticipation[exerciseTypeName + ' ' + SCORE_KEY] = '';
                }
            }
            rows.push(rowDataParticipation);

            // successful
            // TODO adjust for exams
            const rowDataParticipationSuccuessful = this.emptyLine('Number of Successful Participations');
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                    const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                    const exerciseParticipationsSuccessful = this.exerciseSuccessfulPerType.get(exerciseType)!;
                    exerciseTitleKeys.forEach((title, index) => {
                        rowDataParticipationSuccuessful[title] = this.toLocaleString(exerciseParticipationsSuccessful[index]);
                    });
                    rowDataParticipationSuccuessful[exerciseTypeName + ' ' + POINTS_KEY] = '';
                    rowDataParticipationSuccuessful[exerciseTypeName + ' ' + SCORE_KEY] = '';
                }
            }
            rows.push(rowDataParticipationSuccuessful);

            const options = {
                fieldSeparator: ';', // TODO: allow user to customize
                quoteStrings: '"',
                decimalSeparator: 'locale',
                showLabels: true,
                showTitle: false,
                filename: 'Artemis Exam ' + this.exam.title + ' in Course ' + this.exam.course.title + ' Scores',
                useTextFile: false,
                useBom: true,
                headers: keys,
            };

            const csvExporter = new ExportToCsv(options);
            csvExporter.generateCsv(rows); // includes download
        }
    }

    /**
     * Return an empty line in csv-format with an empty row for each exercise type.
     * @param firstValue The first value/name key of the line
     */
    private emptyLine(firstValue: string) {
        const emptyLine = {};
        emptyLine[NAME_KEY] = firstValue;
        emptyLine[USERNAME_KEY] = '';
        emptyLine[EMAIL_KEY] = '';
        emptyLine[REGISTRATION_NUMBER_KEY] = '';
        emptyLine[TOTAL_EXAM_POINTS_KEY] = '';
        emptyLine[TOTAL_EXAM_SCORE_KEY] = '';

        for (const exerciseGroup of this.exerciseGroups) {
            const exerciseGroupName = exerciseGroup.title;
            emptyLine[exerciseGroupName + ' ' + POINTS_KEY] = '';
            emptyLine[exerciseGroupName + ' ' + SCORE_KEY] = '';
        }

        return emptyLine;
    }

    /**
     * Convert a number value to a locale string.
     * @param value
     */
    toLocaleString(value: number) {
        if (isNaN(value)) {
            return '-';
        } else {
            return value.toLocaleString(this.locale, this.options);
        }
    }

    /**
     * Convert a number value to a locale string with a % added at the end.
     * @param value
     */
    toLocalePercentageString(value: number) {
        if (isNaN(value)) {
            return '-';
        } else {
            return value.toLocaleString(this.locale, this.options) + '%';
        }
    }

    /**
     * Unsubscribes from all subscriptions
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}

/**
 * Get the language set by the user.
 */
function getLang() {
    if (navigator.languages !== undefined) {
        return navigator.languages[0];
    } else {
        return navigator.language;
    }
}
