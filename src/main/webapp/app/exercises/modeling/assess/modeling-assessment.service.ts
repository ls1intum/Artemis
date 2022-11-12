import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Feedback } from 'app/entities/feedback.model';
import { Result } from 'app/entities/result.model';
import { map } from 'rxjs/operators';
import { convertDateFromServer } from 'app/utils/date.utils';

export type EntityResponseType = HttpResponse<Result>;

@Injectable({ providedIn: 'root' })
export class ModelingAssessmentService {
    private readonly MAX_FEEDBACK_TEXT_LENGTH = 500;
    private readonly MAX_FEEDBACK_DETAIL_TEXT_LENGTH = 5000;
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    saveAssessment(resultId: number, feedbacks: Feedback[], submissionId: number, submit = false): Observable<Result> {
        let params = new HttpParams();
        if (submit) {
            params = params.set('submit', 'true');
        }
        const url = `${this.resourceUrl}/modeling-submissions/${submissionId}/result/${resultId}/assessment`;
        return this.http.put<Result>(url, feedbacks, { params }).pipe(map((res: Result) => this.convertResult(res)));
    }

    saveExampleAssessment(feedbacks: Feedback[], exampleSubmissionId: number): Observable<Result> {
        const url = `${this.resourceUrl}/modeling-submissions/${exampleSubmissionId}/example-assessment`;
        return this.http.put<Result>(url, feedbacks).pipe(map((res) => this.convertResult(res)));
    }

    updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/modeling-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
        };
        return this.http.put<Result>(url, assessmentUpdate, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertResultEntityResponseTypeFromServer(res)));
    }

    getAssessment(submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/modeling-submissions/${submissionId}/result`).pipe(map((res) => this.convertResult(res)));
    }

    getExampleAssessment(exerciseId: number, submissionId: number): Observable<Result> {
        const url = `${this.resourceUrl}/exercise/${exerciseId}/modeling-submissions/${submissionId}/example-assessment`;
        return this.http.get<Result>(url).pipe(map((res) => this.convertResult(res)));
    }

    cancelAssessment(submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/modeling-submissions/${submissionId}/cancel-assessment`, null);
    }

    /**
     * Deletes an assessment
     * @param participationId id of the participation, to which the assessment and the submission belong to
     * @param submissionId id of the submission, to which the assessment belongs to
     * @param resultId     id of the result which is deleted
     */
    deleteAssessment(participationId: number, submissionId: number, resultId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/participations/${participationId}/modeling-submissions/${submissionId}/results/${resultId}`);
    }

    private convertResultEntityResponseTypeFromServer(res: EntityResponseType): EntityResponseType {
        let result = ModelingAssessmentService.convertItemFromServer(res.body!);
        result = this.convertResult(result);
        result.completionDate = convertDateFromServer(result.completionDate);

        if (result.submission) {
            result.submission.submissionDate = convertDateFromServer(result.submission.submissionDate);
        }
        if (result.participation) {
            result.participation.initializationDate = convertDateFromServer(result.participation.initializationDate);
        }

        return res.clone({ body: result });
    }

    private static convertItemFromServer(result: Result): Result {
        return Object.assign({}, result);
    }

    /**
     * Iterates over all feedback elements of a response and converts the reference field of the feedback into
     * separate referenceType and referenceId fields. The reference field is of the form <referenceType>:<referenceId>.
     */
    convertResult(result: Result): Result {
        if (!result || !result.feedbacks) {
            return result;
        }
        for (const feedback of result.feedbacks) {
            if (feedback.reference) {
                feedback.referenceType = feedback.reference.split(':')[0];
                feedback.referenceId = feedback.reference.split(':')[1];
            }
        }
        return result;
    }

    /**
     * Checks if the feedback text and detail text of every feedback item is smaller than the configured maximum length. Returns true if the length of the texts is valid or if
     * there is no feedback, false otherwise.
     */
    isFeedbackTextValid(feedback: Feedback[]): boolean {
        if (!feedback) {
            return true;
        }
        return feedback.every(
            (feedbackItem) =>
                (!feedbackItem.text || feedbackItem.text.length <= this.MAX_FEEDBACK_TEXT_LENGTH) &&
                (!feedbackItem.detailText || feedbackItem.detailText.length <= this.MAX_FEEDBACK_DETAIL_TEXT_LENGTH),
        );
    }
}
