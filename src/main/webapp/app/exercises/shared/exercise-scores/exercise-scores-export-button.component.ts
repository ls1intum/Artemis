import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { round } from 'app/shared/util/utils';
import { JhiAlertService } from 'ng-jhipster';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Injectable, Component, Input } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { addUserIndependentRepositoryUrl } from 'app/overview/participation-utils';
import { HttpResponse } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { DifferencePipe } from 'ngx-moment';
import { Moment } from 'moment';
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

    constructor(private resultService: ResultService, private momentDiff: DifferencePipe, private jhiAlertService: JhiAlertService) {}

    /**
     * Fetches all results for an exercise and assigns them to the results in this component
     */
    getResults() {
        return this.resultService
            .getResultsForExercise(this.exercise.id!, {
                withSubmissions: this.exercise.type === ExerciseType.MODELING,
            })
            .pipe(
                tap((res: HttpResponse<Result[]>) => {
                    return res.body!.map((result) => {
                        result.participation!.results = [result];
                        (result.participation! as StudentParticipation).exercise = this.exercise;
                        if (result.participation!.type === ParticipationType.PROGRAMMING) {
                            addUserIndependentRepositoryUrl(result.participation!);
                        }
                        result.durationInMinutes = this.durationInMinutes(
                            result.completionDate!,
                            result.participation!.initializationDate ? result.participation!.initializationDate : this.exercise.releaseDate!,
                        );
                        // Nest submission into participation so that it is available for the result component
                        if (result.participation && result.submission) {
                            result.participation.submissions = [result.submission];
                        }
                        return result;
                    });
                }),
            );
    }

    /**
     * Exports the exercise results as a csv file
     */
    exportResults() {
        if (this.exercises.length === 0 && this.exercise !== undefined) {
            this.exercises = this.exercises.concat(this.exercise);
        }
        this.exercises.forEach((exercise) => {
            this.exercise = exercise;
            this.getResults().subscribe((data) => {
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
                        const nameAndUserNameColumnHeaders = studentParticipation.team ? 'Team Name,Team Short Name' : 'Name,Username';
                        const optionalStudentsColumnHeader = studentParticipation.team ? ',Students' : '';
                        if (this.exercise.type !== ExerciseType.PROGRAMMING) {
                            rows.push(`data:text/csv;charset=utf-8,${nameAndUserNameColumnHeaders},Score${optionalStudentsColumnHeader}`);
                        } else {
                            rows.push(`data:text/csv;charset=utf-8,${nameAndUserNameColumnHeaders},Score,Repo Link${optionalStudentsColumnHeader}`);
                        }
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

    private durationInMinutes(completionDate: Moment, initializationDate: Moment) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }
}
