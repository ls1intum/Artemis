import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { JhiDateUtils } from 'ng-jhipster';

import { Result } from './result.model';
import { createRequestOption } from '../../shared';
import { Feedback } from '../feedback';

export type EntityResponseType = HttpResponse<Result>;

@Injectable()
export class ResultService {

    private courseResourceUrl = SERVER_API_URL + 'api/courses';
    private resultResourceUrl = SERVER_API_URL + 'api/results';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) {
    }

    create(result: Result): Observable<EntityResponseType> {
        const copy = this.convert(result);
        return this.http.post<Result>(this.resultResourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(result: Result): Observable<EntityResponseType> {
        const copy = this.convert(result);
        return this.http.put<Result>(this.resultResourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Result>(`${this.resultResourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    findBySubmissionId(submissionId: number): Observable<EntityResponseType> {
        return this.http.get<Result>(`${this.resultResourceUrl}/submission/${submissionId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    findResultsForParticipation(courseId: number, exerciseId: number, participationId: number, req?: any): Observable<HttpResponse<Result[]>> {
        const options = createRequestOption(req);
        return this.http.get(`${this.courseResourceUrl}/${courseId}/exercises/${exerciseId}/participations/${participationId}/results`, { params: options, observe: 'response' })
            .map((res: HttpResponse<Result[]>) => this.convertArrayResponse(res));
    }

    getResultsForExercise(courseId: number, exerciseId: number, req?: any): Observable<HttpResponse<Result[]>> {
        const options = createRequestOption(req);
        return this.http.get<Result[]>(`${this.courseResourceUrl}/${courseId}/exercises/${exerciseId}/results`, { params: options, observe: 'response' });
    }

    getFeedbackDetailsForResult(resultId: number): Observable<HttpResponse<Feedback[]>> {
        return this.http.get<Feedback[]>(`${this.resultResourceUrl}/${resultId}/details`, { observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resultResourceUrl}/${id}`, { observe: 'response' });
    }

    private convertArrayResponse(res: HttpResponse<Result[]>): HttpResponse<Result[]> {
        const jsonResponse: Result[] = res.body;
        const body: Result[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Result = this.convertItemFromServer(res.body);
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to Result.
     */
    private convertItemFromServer(result: Result): Result {
        const copy: Result = Object.assign({}, result);
        copy.completionDate = this.dateUtils.convertDateTimeFromServer(result.completionDate);
        return copy;
    }

    /**
     * Convert a Result to a JSON which can be sent to the server.
     */
    private convert(result: Result): Result {
        const copy: Result = Object.assign({}, result);
        copy.completionDate = this.dateUtils.toDate(result.completionDate);
        return copy;
    }

}
