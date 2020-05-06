import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Feedback } from 'app/entities/feedback.model';
import { EntityResponseType, ResultService } from 'app/exercises/shared/result/result.service';
import { Result } from 'app/entities/result.model';

@Injectable({ providedIn: 'root' })
export class ProgrammingAssessmentManualResultService {
    // TODO: It would be good to refactor the convertDate methods into a separate service, so that we don't have to import the result service here.
    constructor(private http: HttpClient, private resultService: ResultService) {}

    /**
     * Creates a new manual result and stores it in the server
     * @param {number} participationId - Id of the participation
     * @param {Result} result - The result to be created and sent to the server
     */
    create(participationId: number, result: Result): Observable<EntityResponseType> {
        const copy = this.resultService.convertDateFromClient(result);
        // NOTE: we deviate from the standard URL scheme to avoid conflicts with a different POST request on results
        return this.http
            .post<Result>(SERVER_API_URL + 'api/participations/' + participationId + '/manual-results', copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.resultService.convertDateFromServer(res));
    }

    /**
     * Updates an existing manual result in the server
     * @param {number} participationId - Id of the participation
     * @param {Result} result - Updated result to be stored in the server
     */
    update(participationId: number, result: Result): Observable<EntityResponseType> {
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

    /**
     * Creates a new manual result with default values successful=true and score=100
     * @return Created result
     */
    generateInitialManualResult() {
        const newResult = new Result();
        newResult.successful = true;
        newResult.score = 100;
        return newResult;
    }
}
