import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Result } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { buildUrlWithParams } from 'app/utils/global.utils';
import * as moment from 'moment';

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
            url = buildUrlWithParams(url, ['submit=true']);
        }
        return this.http.put<Result>(url, feedbacks);
    }

    updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/file-upload-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
        };
        return this.http
            .put<Result>(url, assessmentUpdate, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    getAssessment(submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/file-upload-submissions/${submissionId}/result`);
    }

    cancelAssessment(submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/file-upload-submissions/${submissionId}/cancel-assessment`, null);
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const result = this.convertItemFromServer(res.body!);

        if (result.completionDate) {
            result.completionDate = moment(result.completionDate);
        }
        if (result.submission && result.submission.submissionDate) {
            result.submission.submissionDate = moment(result.submission.submissionDate);
        }
        if (result.participation && result.participation.initializationDate) {
            result.participation.initializationDate = moment(result.participation.initializationDate);
        }

        return res.clone({ body: result });
    }

    private convertItemFromServer(result: Result): Result {
        return Object.assign({}, result);
    }
}
