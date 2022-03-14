import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { Component, Injectable, Input } from '@angular/core';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { ResultWithPointsPerGradingCriterion } from 'app/entities/result-with-points-per-grading-criterion.model';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ExportToCsv } from 'export-to-csv';

@Component({
    selector: 'jhi-exercise-scores-export-button',
    template: `
        <button class="btn btn-info btn-sm me-1" (click)="exportResults()">
            <fa-icon [icon]="faDownload"></fa-icon>
            <span class="d-none d-md-inline" jhiTranslate="artemisApp.exercise.exportResults">Export Results</span>
        </button>
    `,
})
@Injectable({ providedIn: 'root' })
export class ExerciseScoresExportButtonComponent {
    @Input() exercises: Exercise[] = []; // Used to export multiple scores together
    @Input() exercise: Exercise | ProgrammingExercise;

    // Icons
    faDownload = faDownload;

    constructor(private resultService: ResultService, private alertService: AlertService) {}

    /**
     * Exports the exercise results as a CSV file.
     */
    exportResults() {
        if (this.exercises.length === 0 && this.exercise !== undefined) {
            this.exercises = this.exercises.concat(this.exercise);
        }

        this.exercises.forEach((exercise) => this.constructCSV(exercise));
    }

    /**
     * Builds the CSV with results and triggers the download to the user for it.
     * @param exercise for which the results should be exported.
     * @private
     */
    private constructCSV(exercise: Exercise) {
        this.resultService.getResultsWithPointsPerGradingCriterion(exercise).subscribe((data) => {
            const results: ResultWithPointsPerGradingCriterion[] = data.body || [];
            if (results.length === 0) {
                this.alertService.warning(`artemisApp.exercise.exportResultsEmptyError`, { exercise: exercise.title });
                window.scroll(0, 0);
                return;
            }
            const isTeamExercise = !!(results[0].result.participation! as StudentParticipation).team;
            const gradingCriteria: GradingCriterion[] = ExerciseScoresExportButtonComponent.sortedGradingCriteria(exercise);

            const keys = ExerciseScoresRowBuilder.keys(exercise, isTeamExercise, gradingCriteria);
            const rows = results.map((resultWithPoints) => {
                const studentParticipation = resultWithPoints.result.participation! as StudentParticipation;
                return new ExerciseScoresRowBuilder(exercise, gradingCriteria, studentParticipation, resultWithPoints).build();
            });

            const fileNamePrefix = exercise.shortName ?? exercise.title?.split(/\s+/).join('_');
            ExerciseScoresExportButtonComponent.exportAsCsv(`${fileNamePrefix}-results-scores.csv`, keys, rows);
        });
    }

    /**
     * Triggers the download as CSV for the exercise results.
     * @param filename The filename the results should be downloaded as.
     * @param keys The column names in the CSV.
     * @param rows The actual data rows in the CSV.
     * @private
     */
    private static exportAsCsv(filename: string, keys: string[], rows: ExerciseScoresRow[]) {
        const options = {
            fieldSeparator: ';',
            quoteStrings: '"',
            decimalSeparator: 'locale',
            showLabels: true,
            showTitle: false,
            filename,
            useTextFile: false,
            useBom: true,
            headers: keys,
        };

        const csvExporter = new ExportToCsv(options);
        csvExporter.generateCsv(rows); // includes download
    }

    /**
     * Sorts the list of grading criteria for the given exercise by title ascending.
     * @param exercise which has a list of grading criteria.
     * @private
     */
    private static sortedGradingCriteria(exercise: Exercise): GradingCriterion[] {
        return (
            exercise.gradingCriteria?.sort((crit1, crit2) => {
                if (crit1.title < crit2.title) {
                    return -1;
                } else if (crit1.title > crit2.title) {
                    return 1;
                } else {
                    return 0;
                }
            }) || []
        );
    }
}

/**
 * A data row in the CSV file.
 *
 * For a list of all possible keys see {@link ExerciseScoresRowBuilder.keys}.
 */
type ExerciseScoresRow = any;

class ExerciseScoresRowBuilder {
    private readonly exercise: Exercise;
    private readonly gradingCriteria: GradingCriterion[];
    private readonly participation: StudentParticipation;
    private readonly resultWithPoints: ResultWithPointsPerGradingCriterion;

    private csvRow: ExerciseScoresRow = {};

    constructor(exercise: Exercise, gradingCriteria: GradingCriterion[], participation: StudentParticipation, resultWithPoints: ResultWithPointsPerGradingCriterion) {
        this.exercise = exercise;
        this.gradingCriteria = gradingCriteria;
        this.participation = participation;
        this.resultWithPoints = resultWithPoints;
    }

    /**
     * Builds the actual data row that should be exported as part of the CSV.
     */
    public build(): ExerciseScoresRow {
        this.setName();

        const score = roundValueSpecifiedByCourseSettings(this.resultWithPoints.result.score, getCourseFromExercise(this.exercise));
        this.set('Score', score);
        this.set('Points', this.resultWithPoints.totalPoints);

        this.setGradingCriteriaPoints();
        this.setProgrammingExerciseInformation();
        this.setTeamInformation();

        return this.csvRow;
    }

    /**
     * Stores the given value under the key in the row.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row. Replaced by the empty string if undefined.
     */
    private set<T>(key: string, value: T) {
        this.csvRow[key] = value ?? '';
    }

    /**
     * Sets the student or team name information in the row.
     * @private
     */
    private setName() {
        if (this.participation.team) {
            this.set('Team Name', this.participation.participantName);
            this.set('Team Short Name', this.participation.participantIdentifier);
        } else {
            this.set('Name', this.participation.participantName);
            this.set('Username', this.participation.participantIdentifier);
        }
    }

    /**
     * Sets the points for each grading criterion in the row.
     * @private
     */
    private setGradingCriteriaPoints() {
        let unnamedCriterionIndex = 1;
        this.gradingCriteria.forEach((criterion) => {
            const points = this.resultWithPoints.pointsPerCriterion.get(criterion.id!) || 0;
            if (criterion.title) {
                this.set(criterion.title, points);
            } else {
                this.set(`Unnamed Criterion ${unnamedCriterionIndex}`, points);
                unnamedCriterionIndex += 1;
            }
        });
    }

    /**
     * Adds information specific to programming exercises to the row.
     * @private
     */
    private setProgrammingExerciseInformation() {
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const repoLink = (this.participation as ProgrammingExerciseStudentParticipation).repositoryUrl;
            this.set('Repo Link', repoLink);
        }
    }

    /**
     * Adds information specific to a team participation to the row.
     * @private
     */
    private setTeamInformation() {
        if (this.participation.team) {
            const students = `${this.participation.team?.students?.map((s) => s.name).join(', ')}`;
            this.set('Students', students);
        }
    }

    /**
     * CSV columns [alternative column name for team exercises]:
     * - Name [Team Name]
     * - Username [Team Short Name]
     * - Score
     * - Points
     * - for each grading criterion `c` of the exercise: `c.title`
     *   (sorted by title, contains the total points given in this grading category)
     * - Repo Link (only for programming exercises)
     * - Students (only for team exercises; comma-separated list of students in the team)
     *
     * @param exercise The exercise for which results should be exported.
     * @param isTeamExercise True, if the students participate in teams in this exercise.
     * @param gradingCriteria The grading criteria that can be used in this exercise.
     */
    public static keys(exercise: Exercise, isTeamExercise: boolean, gradingCriteria: GradingCriterion[]): Array<string> {
        const columns = [];

        if (isTeamExercise) {
            columns.push('Team Name', 'Team Short Name');
        } else {
            columns.push('Name', 'Username');
        }

        columns.push('Score', 'Points');

        let unnamedCriterionIndex = 1;
        gradingCriteria.forEach((criterion) => {
            if (criterion.title) {
                columns.push(criterion.title);
            } else {
                columns.push(`Unnamed Criterion ${unnamedCriterionIndex}`);
                unnamedCriterionIndex += 1;
            }
        });

        if (exercise.type === ExerciseType.PROGRAMMING) {
            columns.push('Repo Link');
        }

        if (isTeamExercise) {
            columns.push('Students');
        }

        return columns;
    }
}
