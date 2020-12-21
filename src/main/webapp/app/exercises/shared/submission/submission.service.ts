import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import * as moment from 'moment';
import { createRequestOption } from 'app/shared/util/request-util';
import { Result } from 'app/entities/result.model';
import { getLatestSubmissionResult, setLatestSubmissionResult, Submission } from 'app/entities/submission.model';
import { filter, map, tap } from 'rxjs/operators';
import { TextSubmission } from 'app/entities/text-submission.model';

export type EntityResponseType = HttpResponse<Submission>;
export type EntityArrayResponseType = HttpResponse<Submission[]>;

@Injectable({ providedIn: 'root' })
export class SubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/submissions';
    public resourceUrlParticipation = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient) {}

    /**
     * Delete an existing submission
     * @param submissionId - The id of the submission to be deleted
     * @param req - A request with additional options in it
     */
    delete(submissionId: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`${this.resourceUrl}/${submissionId}`, { params: options, observe: 'response' });
    }

    /**
     * Find all submissions of a given participation
     * @param {number} participationId - The id of the participation to be searched for
     */
    findAllSubmissionsOfParticipation(participationId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Submission[]>(`${this.resourceUrlParticipation}/${participationId}/submissions`, { observe: 'response' })
            .pipe(
                map((res) => this.convertDateArrayFromServer(res)),
                filter((res) => !!res.body),
                tap((res) =>
                    res.body!.forEach((submission) => {
                        this.reconnectSubmissionAndResult(submission);
                    }),
                ),
            );
    }

    /**
     * reconnect submission and result
     * @param submission
     */
    private reconnectSubmissionAndResult(submission: Submission) {
        const result = getLatestSubmissionResult(submission);
        if (result) {
            setLatestSubmissionResult(submission, result);
            result.submission = submission;
        }
    }

    convertResultsDateFromServer(results?: Result[]) {
        const convertedResults: Result[] = [];
        if (results != undefined && results.length > 0) {
            results.forEach((result: Result) => {
                result.completionDate = result.completionDate ? moment(result.completionDate) : undefined;
                convertedResults.push(result);
            });
        }
        return convertedResults;
    }

    convertSubmissionsDateFromServer(submissions?: Submission[]) {
        const convertedSubmissions: Submission[] = [];
        if (submissions != undefined && submissions.length > 0) {
            submissions.forEach((submission: Submission) => {
                if (submission !== null) {
                    submission.submissionDate = submission.submissionDate ? moment(submission.submissionDate) : undefined;
                    this.reconnectSubmissionAndResult(submission);
                    convertedSubmissions.push(submission);
                }
            });
        }
        return convertedSubmissions;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            this.convertSubmissionsDateFromServer(res.body);
        }
        return res;
    }

    getTestRunSubmissionsForExercise(exerciseId: number): Observable<HttpResponse<Submission[]>> {
        return this.http
            .get<TextSubmission[]>(`api/exercises/${exerciseId}/test-run-submissions`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<TextSubmission[]>) => this.convertArrayResponse(res)));
    }

    private convertArrayResponse(res: HttpResponse<Submission[]>): HttpResponse<Submission[]> {
        const jsonResponse: Submission[] = res.body!;
        const body: Submission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to TextSubmission.
     */
    private convertItemFromServer(submission: Submission): Submission {
        return Object.assign({}, submission);
    }
}
