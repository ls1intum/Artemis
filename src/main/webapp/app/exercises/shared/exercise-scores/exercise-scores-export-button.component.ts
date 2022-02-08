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
     *
     * CSV columns [alternative column name for team exercises]:
     * - Name [Team Name]
     * - Username [Team Short Name]
     * - Score
     * - Points
     * - for each grading criterion `c` of the exercise: `c.title`
     *   (sorted by title, contains the total points given in this grading category)
     * - Repo Link (only for programming exercises)
     * - Students (only for team exercises; comma-separated list of students in the team)
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
            const rows: string[] = [];
            const results: ResultWithPointsPerGradingCriterion[] = data.body || [];
            if (results.length === 0) {
                this.alertService.warning(`artemisApp.exercise.exportResultsEmptyError`, { exercise: exercise.title });
                window.scroll(0, 0);
                return;
            }
            const gradingCriteria: GradingCriterion[] = ExerciseScoresExportButtonComponent.sortedGradingCriteria(exercise);

            results.forEach((resultWithPoints, index) => {
                const studentParticipation = resultWithPoints.result.participation! as StudentParticipation;
                if (index === 0) {
                    rows.push(this.formatHeaderRow(exercise, studentParticipation, gradingCriteria));
                }
                rows.push(this.formatCsvRow(exercise, gradingCriteria, studentParticipation, resultWithPoints));
            });

            const fileNamePrefix = exercise.shortName ?? exercise.title?.split(/\s+/).join('_');
            this.resultService.triggerDownloadCSV(rows, `${fileNamePrefix}-results-scores.csv`);
        });
    }

    /**
     * Utility method used to retrieve the headers row for CSV creation
     * @param exercise the exercise the participation belongs to
     * @param studentParticipation to create headers for
     * @param gradingCriteria the list of grading criteria for which the points should be included in the CSV
     */
    private formatHeaderRow(exercise: Exercise, studentParticipation: StudentParticipation, gradingCriteria: GradingCriterion[]) {
        const columns = [];

        if (studentParticipation.team) {
            columns.push('Team Name', 'Team Short Name');
        } else {
            columns.push('Name', 'Username');
        }
        columns.push('Score', 'Points');

        gradingCriteria.forEach((criterion) => columns.push(`"${criterion.title ?? 'Unnamed Criterion'}"`));

        if (exercise.type === ExerciseType.PROGRAMMING) {
            columns.push('Repo Link');
        }

        if (studentParticipation.team) {
            columns.push('Students');
        }

        return 'data:text/csv;charset=utf-8,' + columns.join(',');
    }

    /**
     * Formats a single row of the CSV file
     * @param exercise of which the results are processed
     * @param gradingCriteria sorted in the same order as they appear in the header of the CSV
     * @param participation of the student in this exercise
     * @param resultWithPoints the result together with the points per grading criterion
     * @private
     */
    private formatCsvRow(
        exercise: Exercise,
        gradingCriteria: GradingCriterion[],
        participation: StudentParticipation,
        resultWithPoints: ResultWithPointsPerGradingCriterion,
    ): string {
        const result = resultWithPoints.result;
        const { participantName, participantIdentifier } = participation;
        const score = roundValueSpecifiedByCourseSettings(result.score, getCourseFromExercise(exercise));

        const columns = [participantName, participantIdentifier, score, resultWithPoints.totalPoints];

        gradingCriteria.map((criterion) => resultWithPoints.pointsPerCriterion.get(criterion.id!) || 0).forEach((criterionPoints) => columns.push(criterionPoints));

        if (exercise.type === ExerciseType.PROGRAMMING) {
            const repoLink = (participation as ProgrammingExerciseStudentParticipation).repositoryUrl;
            columns.push(repoLink);
        }

        if (participation.team) {
            const students = `"${participation.team?.students?.map((s) => s.name).join(', ')}"`;
            columns.push(students);
        }

        return columns.join(',');
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
