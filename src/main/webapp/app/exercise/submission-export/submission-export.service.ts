import { Service, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FileUploadExerciseApi } from 'app/openapi/api/file-upload-exercise-api';
import { SubmissionExportOptions as SubmissionExportOptionsRequest } from 'app/openapi/model/submission-export-options';
import { convertDateFromClient } from 'app/foundation/util/date.utils';

export type SubmissionExportOptions = {
    exportAllParticipants: boolean;
    filterLateSubmissions: boolean;
    filterLateSubmissionsDate: dayjs.Dayjs | null;
    participantIdentifierList: string; // comma separated
};

@Service()
export class SubmissionExportService {
    private http = inject(HttpClient);
    private fileUploadExerciseApi = inject(FileUploadExerciseApi);

    public resourceUrl = 'api';

    /**
     * Exports submissions to the server by their participant identifiers
     * @param {number} exerciseId - Id of the exercise
     * @param {ExerciseType} exerciseType - Type of the exercise
     * @param {SubmissionExportOptions} repositoryExportOptions
     */
    exportSubmissions(exerciseId: number, exerciseType: ExerciseType, repositoryExportOptions: SubmissionExportOptions): Observable<HttpResponse<Blob>> {
        if (exerciseType === ExerciseType.FILE_UPLOAD) {
            return this.fileUploadExerciseApi.exportSubmissions(exerciseId, toSubmissionExportOptionsRequest(repositoryExportOptions));
        }
        return this.http.post(`${this.resourceUrl}/${this.getExerciseUrl(exerciseType, exerciseId)}/export-submissions`, repositoryExportOptions, {
            observe: 'response',
            responseType: 'blob',
        });
    }

    getExerciseUrl(exerciseType: ExerciseType, exerciseId: number) {
        switch (exerciseType) {
            case ExerciseType.TEXT:
                return 'text/text-exercises/' + exerciseId;
            case ExerciseType.MODELING:
                return 'modeling/modeling-exercises/' + exerciseId;
            default:
                throw Error('Export not implemented for exercise type ' + exerciseType);
        }
    }
}

/** Converts the dialog options into the export request; a missing late-submission date is left out, which the server reads as no date. */
function toSubmissionExportOptionsRequest(options: SubmissionExportOptions): SubmissionExportOptionsRequest {
    return {
        exportAllParticipants: options.exportAllParticipants,
        filterLateSubmissions: options.filterLateSubmissions,
        filterLateSubmissionsDate: convertDateFromClient(options.filterLateSubmissionsDate ?? undefined),
        participantIdentifierList: options.participantIdentifierList,
    };
}
