import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { map } from 'rxjs/operators';

import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { stringifyCircular } from 'app/shared/util/utils';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';

export type EntityResponseType = HttpResponse<FileUploadSubmission>;

@Injectable({ providedIn: 'root' })
export class FileUploadSubmissionService {
    constructor(private http: HttpClient) {}

    /**
     * Updates File Upload submission on the server
     * @param fileUploadSubmission that will be updated on the server
     * @param exerciseId id of the exercise
     * @param submissionFile the file submitted that will for the exercise
     */
    update(fileUploadSubmission: FileUploadSubmission, exerciseId: number, submissionFile: Blob | File): Observable<EntityResponseType> {
        const copy = FileUploadSubmissionService.convert(fileUploadSubmission);
        const formData = new FormData();
        const submissionBlob = new Blob([stringifyCircular(copy)], { type: 'application/json' });
        formData.append('file', submissionFile);
        formData.append('submission', submissionBlob);
        return this.http
            .post<FileUploadSubmission>(`api/exercises/${exerciseId}/file-upload-submissions`, formData, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Returns File Upload submission from the server
     * @param fileUploadSubmissionId the id of the File Upload submission
     */
    get(fileUploadSubmissionId: number): Observable<HttpResponse<FileUploadSubmission>> {
        return this.http
            .get<FileUploadSubmission>(`api/file-upload-submissions/${fileUploadSubmissionId}`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<FileUploadSubmission>) => this.convertResponse(res)));
    }

    /**
     * Returns File Upload submissions for exercise from the server
     * @param exerciseId the id of the exercise
     * @param req request parameters
     * @correctionRound correctionRound for which to get the Submissions
     */
    getFileUploadSubmissionsForExerciseByCorrectionRound(
        exerciseId: number,
        req: { submittedOnly?: boolean; assessedByTutor?: boolean },
        correctionRound?: number,
    ): Observable<HttpResponse<FileUploadSubmission[]>> {
        correctionRound = correctionRound ?? 0;
        const options = createRequestOption(req);
        return this.http
            .get<FileUploadSubmission[]>(`api/exercises/${exerciseId}/round/${correctionRound}/file-upload-submissions`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<FileUploadSubmission[]>) => this.convertArrayResponse(res)));
    }

    /**
     * Returns next File Upload submission without assessment from the server
     * @param exerciseId the id of the exercise
     */
    getFileUploadSubmissionForExerciseForCorrectionRoundWithoutAssessment(exerciseId: number, lock?: boolean, correctionRound?: number): Observable<FileUploadSubmission> {
        correctionRound = correctionRound ?? 0;
        let url = `api/exercises/${exerciseId}/round/${correctionRound}/file-upload-submission-without-assessment`;
        if (lock) {
            url += '?lock=true';
        }
        return this.http.get<FileUploadSubmission>(url).pipe(map((res: FileUploadSubmission) => FileUploadSubmissionService.convertItemFromServer(res)));
    }

    /**
     * Returns data for File Upload editor from the server
     * @param participationId the id of the participation
     */
    getDataForFileUploadEditor(participationId: number): Observable<FileUploadSubmission> {
        return this.http.get<FileUploadSubmission>(`api/participations/${participationId}/file-upload-editor`, { responseType: 'json' });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: FileUploadSubmission = FileUploadSubmissionService.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<FileUploadSubmission[]>): HttpResponse<FileUploadSubmission[]> {
        const jsonResponse: FileUploadSubmission[] = res.body!;
        const body: FileUploadSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(FileUploadSubmissionService.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to FileUploadSubmission.
     */
    private static convertItemFromServer(fileUploadSubmission: FileUploadSubmission): FileUploadSubmission {
        const convertedFileUploadSubmission = Object.assign({}, fileUploadSubmission);
        setLatestSubmissionResult(convertedFileUploadSubmission, getLatestSubmissionResult(convertedFileUploadSubmission));
        return convertedFileUploadSubmission;
    }

    /**
     * Convert a FileUploadSubmission to a JSON which can be sent to the server.
     */
    private static convert(fileUploadSubmission: FileUploadSubmission): FileUploadSubmission {
        return Object.assign({}, fileUploadSubmission);
    }
}
