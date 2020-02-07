import { Injectable } from '@angular/core';
import { EntityResponseType, Result, ResultService } from 'app/entities/result';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import * as moment from 'moment';
import { Feedback } from 'app/entities/feedback';
import { ComplaintResponse } from 'app/entities/complaint-response';

@Injectable({ providedIn: 'root' })
export class ProgrammingAssessmentManualResultService {
    // TODO: It would be good to refactor the convertDate methods into a separate service, so that we don't have to import the result service here.
    constructor(private http: HttpClient, private resultService: ResultService) {}

    create(participationId: number, result: Result): Observable<EntityResponseType> {
        const copy = this.resultService.convertDateFromClient(result);
        // NOTE: we deviate from the standard URL scheme to avoid conflicts with a different POST request on results
        return this.http
            .post<Result>(SERVER_API_URL + 'api/participations/' + participationId + '/manual-results', copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.resultService.convertDateFromServer(res));
    }

    update(participationId: number, result: Result): Observable<EntityResponseType> {
        // TODO: This is a problem with the client side modeling of the participation: It is possible that a participation is sent from the server that does not have a result array.
        const copy = this.resultService.convertDateFromClient(result) as any;
        // This needs to be removed to avoid a circular serialization issue.
        copy.participation!.results = undefined;
        copy.participation!.submissions = undefined;
        if (copy.submission && copy.submission.result) {
            copy.submission.result.submission = undefined;
        }
        return this.http
            .put<Result>(SERVER_API_URL + 'api/participations/' + participationId + '/manual-results', copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.resultService.convertDateFromServer(res));
    }

    /**
     * Send the request to update the assessment after the complaint (only done once per complaint). The result score, string
     * and feedbacks will be updated. Original result will be stored as a string on complaint.
     * @param feedbacks list of feedback items (the score is not evaluated from them, as we pass score directly from the result)
     * @param complaintResponse contains main information about the complaint response (time, responseText, reviewer)
     * @param result updated result (only score and resultString is updated)
     * @param submissionId the id of the submission
     * @return updated result with updated feedbacks and score
     */
    updateAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, result: Result, submissionId: number): Observable<Result> {
        const url = `${SERVER_API_URL}api/programming-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
            score: result.score,
            resultString: result.resultString,
        };
        return this.http.put<Result>(url, assessmentUpdate);
    }

    generateInitialManualResult() {
        const newResult = new Result();
        newResult.successful = true;
        newResult.score = 100;
        return newResult;
    }
}
