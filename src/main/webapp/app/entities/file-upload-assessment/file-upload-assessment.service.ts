import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Result } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';
import { ComplaintResponse } from 'app/entities/complaint-response';

export type EntityResponseType = HttpResponse<Result>;

@Injectable({
    providedIn: 'root',
})
export class FileUploadAssessmentsService {
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    saveAssessment(feedbacks: Feedback[], submissionId: number, submit = false): Observable<Result> {
        let url = `${this.resourceUrl}/file-upload-submissions/${submissionId}/feedback`;
        if (submit) {
            url += '?submit=true';
        }
        return this.http.put<Result>(url, feedbacks);
    }

    updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number): Observable<Result> {
        const url = `${this.resourceUrl}/file-upload-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
        };
        return this.http.put<Result>(url, assessmentUpdate);
    }

    getAssessment(submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/file-upload-submissions/${submissionId}/result`);
    }

    cancelAssessment(submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/file-upload-submissions/${submissionId}/cancel-assessment`, null);
    }
}
