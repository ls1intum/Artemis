import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { FileUploadSubmission } from './file-upload-submission.model';
import { createRequestOption } from 'app/shared';

export type EntityResponseType = HttpResponse<FileUploadSubmission>;

@Injectable({ providedIn: 'root' })
export class FileUploadSubmissionService {
    constructor(private http: HttpClient) {}

    create(fileUploadSubmission: FileUploadSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(fileUploadSubmission);
        return this.http
            .post<FileUploadSubmission>(`api/exercises/${exerciseId}/file-upload-submissions`, copy, {
                observe: 'response',
            })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(fileUploadSubmission: FileUploadSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(fileUploadSubmission);
        return this.http
            .put<FileUploadSubmission>(`api/exercises/${exerciseId}/file-upload-submissions`, copy, {
                observe: 'response',
            })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    get(fileUploadSubmissionId: number): Observable<HttpResponse<FileUploadSubmission>> {
        return this.http
            .get<FileUploadSubmission>(`api/file-upload-submissions/${fileUploadSubmissionId}`, {
                observe: 'response',
            })
            .map((res: HttpResponse<FileUploadSubmission>) => this.convertResponse(res));
    }

    getFileUploadSubmissionsForExercise(exerciseId: number, req: { submittedOnly?: boolean; assessedByTutor?: boolean }): Observable<HttpResponse<FileUploadSubmission[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<FileUploadSubmission[]>(`api/exercises/${exerciseId}/file-upload-submissions`, {
                params: options,
                observe: 'response',
            })
            .map((res: HttpResponse<FileUploadSubmission[]>) => this.convertArrayResponse(res));
    }

    getFileUploadSubmissionForExerciseWithoutAssessment(exerciseId: number): Observable<FileUploadSubmission> {
        return this.http.get<FileUploadSubmission>(`api/exercises/${exerciseId}/file-upload-submission-without-assessment`);
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: FileUploadSubmission = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<FileUploadSubmission[]>): HttpResponse<FileUploadSubmission[]> {
        const jsonResponse: FileUploadSubmission[] = res.body!;
        const body: FileUploadSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to FileUploadSubmission.
     */
    private convertItemFromServer(fileUploadSubmission: FileUploadSubmission): FileUploadSubmission {
        return Object.assign({}, fileUploadSubmission);
    }

    /**
     * Convert a FileUploadSubmission to a JSON which can be sent to the server.
     */
    private convert(fileUploadSubmission: FileUploadSubmission): FileUploadSubmission {
        return Object.assign({}, fileUploadSubmission);
    }
}
