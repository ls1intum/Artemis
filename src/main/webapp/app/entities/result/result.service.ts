import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import * as moment from 'moment';

import { Result } from './result.model';
import { createRequestOption } from '../../shared';
import { Feedback } from '../feedback';

export type EntityResponseType = HttpResponse<Result>;
export type EntityArrayResponseType = HttpResponse<Result[]>;

@Injectable()
export class ResultService {
    private courseResourceUrl = SERVER_API_URL + 'api/courses';
    private resultResourceUrl = SERVER_API_URL + 'api/results';

    constructor(private http: HttpClient) {}

    create(result: Result): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(result);
        return this.http
            .post<Result>(this.resultResourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(result: Result): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(result);
        return this.http
            .put<Result>(this.resultResourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resultResourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findBySubmissionId(submissionId: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resultResourceUrl}/submission/${submissionId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findResultsForParticipation(
        courseId: number,
        exerciseId: number,
        participationId: number,
        req?: any
    ): Observable<HttpResponse<Result[]>> {
        const options = createRequestOption(req);
        return this.http
            .get(`${this.courseResourceUrl}/${courseId}/exercises/${exerciseId}/participations/${participationId}/results`, {
                params: options,
                observe: 'response'
            })
            .map((res: HttpResponse<Result[]>) => this.convertDateArrayFromServer(res));
    }

    getResultsForExercise(courseId: number, exerciseId: number, req?: any): Observable<HttpResponse<Result[]>> {
        const options = createRequestOption(req);
        return this.http.get<Result[]>(`${this.courseResourceUrl}/${courseId}/exercises/${exerciseId}/results`, {
            params: options,
            observe: 'response'
        });
    }

    getFeedbackDetailsForResult(resultId: number): Observable<HttpResponse<Feedback[]>> {
        return this.http.get<Feedback[]>(`${this.resultResourceUrl}/${resultId}/details`, { observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resultResourceUrl}/${id}`, { observe: 'response' });
    }

    private convertDateFromClient(result: Result): Result {
        const copy: Result = Object.assign({}, result, {
            completionDate: result.completionDate != null && result.completionDate.isValid() ? result.completionDate.toJSON() : null
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.completionDate = res.body.completionDate != null ? moment(res.body.completionDate) : null;
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((result: Result) => {
            result.completionDate = result.completionDate != null ? moment(result.completionDate) : null;
        });
        return res;
    }
}
