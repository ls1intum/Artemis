import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { round } from 'app/shared/util/utils';
import { JhiAlertService } from 'ng-jhipster';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Injectable, Component, Input } from '@angular/core';

import { ResultService } from 'app/exercises/shared/result/result.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-exercise-scores-export-button',
    template: `
        <button class="btn btn-info btn-sm mr-1" (click)="exportResults()">
            <fa-icon [icon]="'download'"></fa-icon>
            <span class="d-none d-md-inline" jhiTranslate="artemisApp.exercise.exportResults">Export Results</span>
        </button>
    `,
})
@Injectable({ providedIn: 'root' })
export class ExerciseScoresExportButtonComponent {
    @Input() exercises: Exercise[] = []; // Used to export multiple scores together
    @Input() exercise: Exercise | ProgrammingExercise;

    constructor(private resultService: ResultService, private jhiAlertService: JhiAlertService) {}

    /**
     * Exports the exercise results as a csv file
     */
    exportResults() {
        if (this.exercises.length === 0 && this.exercise !== undefined) {
            this.exercises = this.exercises.concat(this.exercise);
        }
        this.exercises.forEach((exercise) => {
            this.exercise = exercise;
            this.resultService.getResults(exercise).subscribe((data) => {
                const rows: string[] = [];
                const results = data.body || [];
                if (results.length === 0) {
                    this.jhiAlertService.warning(`artemisApp.exercise.exportResultsEmptyError`, { exercise: exercise.title });
                    window.scroll(0, 0);
                    return;
                }
                results.forEach((result, index) => {
                    const studentParticipation = result.participation! as StudentParticipation;
                    const { participantName, participantIdentifier } = studentParticipation;
                    const score = round(result.score);

                    if (index === 0) {
                        rows.push(this.getHeadersRow(studentParticipation));
                    }
                    const optionalStudentsColumnValue = studentParticipation.team ? `,"${studentParticipation.team?.students?.map((s) => s.name).join(', ')}"` : '';
                    if (this.exercise.type !== ExerciseType.PROGRAMMING) {
                        rows.push(`${participantName},${participantIdentifier},${score}${optionalStudentsColumnValue}`);
                    } else {
                        const repoLink = (studentParticipation as ProgrammingExerciseStudentParticipation).repositoryUrl;
                        rows.push(`${participantName},${participantIdentifier},${score},${repoLink}${optionalStudentsColumnValue}`);
                    }
                });
                const csvContent = rows.join('\n');
                const encodedUri = encodeURI(csvContent);
                const link = document.createElement('a');
                link.setAttribute('href', encodedUri);
                link.setAttribute('download', `${exercise.shortName}-results-scores.csv`);
                document.body.appendChild(link); // Required for FF
                link.click();
            });
        });
    }

    /**
     * Utility method used to retrieve the headers row for CSV creation
     * @param studentParticipation to create headers for
     */
    getHeadersRow(studentParticipation: StudentParticipation) {
        const nameAndUserNameColumnHeaders = studentParticipation.team ? 'Team Name,Team Short Name' : 'Name,Username';
        const optionalStudentsColumnHeader = studentParticipation.team ? ',Students' : '';
        if (this.exercise.type !== ExerciseType.PROGRAMMING) {
            return `data:text/csv;charset=utf-8,${nameAndUserNameColumnHeaders},Score${optionalStudentsColumnHeader}`;
        } else {
            return `data:text/csv;charset=utf-8,${nameAndUserNameColumnHeaders},Score,Repo Link${optionalStudentsColumnHeader}`;
        }
    }
}
