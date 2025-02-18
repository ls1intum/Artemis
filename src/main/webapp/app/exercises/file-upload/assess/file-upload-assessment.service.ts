import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Feedback } from 'app/entities/feedback.model';
import { Result } from 'app/entities/result.model';
import { map } from 'rxjs/operators';
import { convertDateFromServer } from 'app/utils/date.utils';

export type EntityResponseType = HttpResponse<Result>;
type FileUploadAssessmentDTO = { feedbacks: Feedback[]; assessmentNote?: string };

@Injectable({
    providedIn: 'root',
})
export class FileUploadAssessmentService {
    private http = inject(HttpClient);

    private resourceUrl = 'api';

    saveAssessment(feedbacks: Feedback[], submissionId: number, assessmentNote: string | undefined, submit = false): Observable<Result> {
        let params = new HttpParams();
        if (submit) {
            params = params.set('submit', 'true');
        }
        const body: FileUploadAssessmentDTO = { feedbacks, assessmentNote };
        const url = `${this.resourceUrl}/file-upload-submissions/${submissionId}/feedback`;
        return this.http.put<Result>(url, body, { params });
    }

    updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number, assessmentNote?: string): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/file-upload-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
            assessmentNote,
        };
        return this.http.put<Result>(url, assessmentUpdate, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertResultEntityResponseTypeFromServer(res)));
    }

    // TODO refactor all asssessment.service getAssessment calls to make similar REST calls
    getAssessment(submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/file-upload-submissions/${submissionId}/result`);
    }

    cancelAssessment(submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/file-upload-submissions/${submissionId}/cancel-assessment`, null);
    }

    /**
     * Deletes an assessment.
     * @param participationId id of the participation, to which the assessment and the submission belong to
     * @param submissionId id of the submission, to which the assessment belongs to
     * @param resultId     id of the result which is deleted
     */
    deleteAssessment(participationId: number, submissionId: number, resultId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/participations/${participationId}/file-upload-submissions/${submissionId}/results/${resultId}`);
    }

    private convertResultEntityResponseTypeFromServer(res: EntityResponseType): EntityResponseType {
        const result = this.convertItemFromServer(res.body!);
        result.completionDate = convertDateFromServer(result.completionDate);
        if (result.submission) {
            result.submission.submissionDate = convertDateFromServer(result.submission.submissionDate);
        }
        if (result.participation) {
            result.participation.initializationDate = convertDateFromServer(result.participation.initializationDate);
        }

        return res.clone({ body: result });
    }

    private convertItemFromServer(result: Result): Result {
        return Object.assign({}, result);
    }
}
