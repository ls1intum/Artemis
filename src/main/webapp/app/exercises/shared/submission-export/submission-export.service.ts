import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/entities/exercise.model';

export type SubmissionExportOptions = {
    exportAllParticipants: boolean;
    filterLateSubmissions: boolean;
    filterLateSubmissionsDate: dayjs.Dayjs | null;
    participantIdentifierList: string; // comma separated
};

@Injectable({ providedIn: 'root' })
export class SubmissionExportService {
    private http = inject(HttpClient);

    public resourceUrl = 'api';

    /**
     * Exports submissions to the server by their participant identifiers
     * @param {number} exerciseId - Id of the exercise
     * @param {ExerciseType} exerciseType - Type of the exercise
     * @param {SubmissionExportOptions} repositoryExportOptions
     */
    exportSubmissions(exerciseId: number, exerciseType: ExerciseType, repositoryExportOptions: SubmissionExportOptions): Observable<HttpResponse<Blob>> {
        return this.http.post(`${this.resourceUrl}/${this.getExerciseUrl(exerciseType, exerciseId)}/export-submissions`, repositoryExportOptions, {
            observe: 'response',
            responseType: 'blob',
        });
    }

    getExerciseUrl(exerciseType: ExerciseType, exerciseId: number) {
        switch (exerciseType) {
            case ExerciseType.TEXT:
                return 'text-exercises/' + exerciseId;
            case ExerciseType.MODELING:
                return 'modeling-exercises/' + exerciseId;
            case ExerciseType.FILE_UPLOAD:
                return 'file-upload-exercises/' + exerciseId;
            default:
                throw Error('Export not implemented for exercise type ' + exerciseType);
        }
    }
}
