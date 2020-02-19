import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import * as moment from 'moment';

import { createRequestOption } from 'app/shared/util/request-util';
import { Result } from 'app/entities/result.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Submission } from 'app/entities/submission.model';
import { filter, map, tap } from 'rxjs/operators';

export type EntityResponseType = HttpResponse<Submission>;
export type EntityArrayResponseType = HttpResponse<Submission[]>;

@Injectable({ providedIn: 'root' })
export class SubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/submissions';
    public resourceUrlParticipation = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient) {}

    delete(submissionId: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`${this.resourceUrl}/${submissionId}`, { params: options, observe: 'response' });
    }

    findAllSubmissionsOfParticipation(participationId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Submission[]>(`${this.resourceUrlParticipation}/${participationId}/submissions`, { observe: 'response' })
            .pipe(
                map(res => this.convertDateArrayFromServer(res)),
                filter(res => !!res.body),
                tap(res =>
                    res.body!.forEach(submission => {
                        // reconnect results to submissions
                        if (submission.result) {
                            submission.result.submission = submission;
                        }
                    }),
                ),
            );
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.submissionDate = res.body.submissionDate != null ? moment(res.body.submissionDate) : null;
            res.body.participation = this.convertParticipationDateFromServer(res.body.participation);
        }
        return res;
    }

    protected convertParticipationDateFromServer(participation: Participation) {
        participation.initializationDate = participation.initializationDate != null ? moment(participation.initializationDate) : null;
        participation.results = this.convertResultsDateFromServer(participation.results);
        participation.submissions = this.convertSubmissionsDateFromServer(participation.submissions);
        return participation;
    }

    protected convertResultsDateFromServer(results: Result[]) {
        const convertedResults: Result[] = [];
        if (results != null && results.length > 0) {
            results.forEach((result: Result) => {
                result.completionDate = result.completionDate != null ? moment(result.completionDate) : null;
                convertedResults.push(result);
            });
        }
        return convertedResults;
    }

    protected convertSubmissionsDateFromServer(submissions: Submission[]) {
        const convertedSubmissions: Submission[] = [];
        if (submissions != null && submissions.length > 0) {
            submissions.forEach((submission: Submission) => {
                if (submission !== null) {
                    submission.submissionDate = submission.submissionDate != null ? moment(submission.submissionDate) : null;
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
}
