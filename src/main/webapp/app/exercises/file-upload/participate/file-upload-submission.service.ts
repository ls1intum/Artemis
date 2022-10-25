import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { stringifyCircular } from 'app/shared/util/utils';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';

export type EntityResponseType = HttpResponse<FileUploadSubmission>;

@Injectable({ providedIn: 'root' })
export class FileUploadSubmissionService {
    constructor(private http: HttpClient, private submissionService: SubmissionService) {}

    /**
     * Updates File Upload submission on the server
     * @param fileUploadSubmission that will be updated on the server
     * @param exerciseId id of the exercise
     * @param submissionFile the file submitted that will for the exercise
     */
    update(fileUploadSubmission: FileUploadSubmission, exerciseId: number, submissionFile: File): Observable<HttpResponse<FileUploadSubmission>> {
        const copy = this.submissionService.convert(fileUploadSubmission);
        const formData = new FormData();
        const submissionBlob = new Blob([stringifyCircular(copy)], { type: 'application/json' });
        formData.append('file', submissionFile);
        formData.append('submission', submissionBlob);
        return this.http
            .post<FileUploadSubmission>(`api/exercises/${exerciseId}/file-upload-submissions`, formData, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertSubmissionResponseFromServer(res)));
    }

    /**
     * Returns File Upload submission from the server
     * @param fileUploadSubmissionId the id of the File Upload submission
     * @param correctionRound
     * @param resultId
     */
    get(fileUploadSubmissionId: number, correctionRound = 0, resultId?: number): Observable<HttpResponse<FileUploadSubmission>> {
        const url = `api/file-upload-submissions/${fileUploadSubmissionId}`;
        let params = new HttpParams();
        if (resultId && resultId > 0) {
            // in case resultId is set, we do not need the correction round
            params = params.set('resultId', resultId!.toString());
        } else {
            params = params.set('correction-round', correctionRound.toString());
        }
        return this.http
            .get<FileUploadSubmission>(url, { params, observe: 'response' })
            .pipe(map((res: HttpResponse<FileUploadSubmission>) => this.submissionService.convertSubmissionResponseFromServer(res)));
    }

    /**
     * Returns File Upload submissions for exercise from the server
     * @param exerciseId the id of the exercise
     * @param req request parameters
     * @param correctionRound for which to get the Submissions
     */
    getSubmissions(exerciseId: number, req: { submittedOnly?: boolean; assessedByTutor?: boolean }, correctionRound = 0): Observable<HttpResponse<FileUploadSubmission[]>> {
        const url = `api/exercises/${exerciseId}/file-upload-submissions`;
        let params = createRequestOption(req);
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        return this.http
            .get<FileUploadSubmission[]>(url, {
                params,
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<FileUploadSubmission[]>) => this.submissionService.convertArrayResponse(res)));
    }

    /**
     * Returns next File Upload submission without assessment from the server
     * @param exerciseId the id of the exercise
     * @param lock
     * @param correctionRound for which to get the Submissions
     */
    getSubmissionWithoutAssessment(exerciseId: number, lock?: boolean, correctionRound = 0): Observable<FileUploadSubmission> {
        const url = `api/exercises/${exerciseId}/file-upload-submission-without-assessment`;
        let params = new HttpParams();
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        if (lock) {
            params = params.set('lock', 'true');
        }

        return this.http.get<FileUploadSubmission>(url, { params }).pipe(map((res: FileUploadSubmission) => this.submissionService.convertSubmissionFromServer(res)));
    }

    /**
     * Returns data for File Upload editor from the server
     * @param participationId the id of the participation
     */
    getDataForFileUploadEditor(participationId: number): Observable<FileUploadSubmission> {
        return this.http
            .get<FileUploadSubmission>(`api/participations/${participationId}/file-upload-editor`, { responseType: 'json' })
            .pipe(map((res: FileUploadSubmission) => this.submissionService.convertSubmissionFromServer(res)));
    }
}
