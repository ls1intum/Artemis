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

    private resourceUrl =  SERVER_API_URL + 'api/results';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    create(result: Result): Observable<EntityResponseType> {
        const copy = this.convert(result);
        return this.http.post<Result>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(result: Result): Observable<EntityResponseType> {
        const copy = this.convert(result);
        return this.http.put<Result>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Result>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    findBySubmissionId(id: number): Observable<EntityResponseType> {
        return this.http.get<Result>(`${this.resourceUrl}/submission/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    details(id: number): Observable<HttpResponse<Feedback[]>> {
        return this.http.get(`${this.resourceUrl}/${id}/details`, { observe: 'response'})
            .map((res: HttpResponse<Feedback[]>) => this.convertFeedbackArrayResponse(res));
    }

    query(req?: any): Observable<HttpResponse<Result[]>> {
        const options = createRequestOption(req);
        return this.http.get<Result[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<Result[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Result = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<Result[]>): HttpResponse<Result[]> {
        const jsonResponse: Result[] = res.body;
        const body: Result[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    private convertFeedbackArrayResponse(res: HttpResponse<Feedback[]>): HttpResponse<Feedback[]> {
        const jsonResponse: Feedback[] = res.body;
        const body: Feedback[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertFeedbackFromServer(jsonResponse[i]));
        }
        return res.clone({body});
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
     * Convert a returned JSON object to Feedback.
     */
    private convertFeedbackFromServer(feedback: Feedback): Feedback {
        const copy: Feedback = Object.assign({}, feedback);
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

// TODO move to its own file

@Injectable()
export class ParticipationResultService {

    private resourceUrl =  SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    find(courseId: number, exerciseId: number, participationId: number, resultId: number): Observable<Result> {
        return this.http.get(
            `${this.resourceUrl}/${courseId}/exercises/${exerciseId}/participations/${participationId}/results/${resultId}`)
            .map((res: HttpResponse<Result>) => {
            return this.convertItemFromServer(res.body);
        });
    }

    query(courseId: number, exerciseId: number, participationId: number, req?: any): Observable<HttpResponse<Result[]>> {
        const options = createRequestOption(req);
        return this.http.get(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/participations/${participationId}/results`, { params: options, observe: 'response' })
            .map((res: HttpResponse<Result[]>) => this.convertArrayResponse(res));
    }

    private convertResponse(res: HttpResponse<Result>): HttpResponse<Result> {
        const body: Result = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<Result[]>): HttpResponse<Result[]> {
        const jsonResponse: Result[] = res.body;
        const body: Result[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Result.
     */
    private convertItemFromServer(json: any): Result {
        const entity: Result = Object.assign(new Result(), json);
        entity.completionDate = this.dateUtils
            .convertDateTimeFromServer(json.completionDate);
        return entity;
    }
}

@Injectable()
export class ExerciseResultService {

    private resourceUrl =  SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) { }

    query(courseId: number, exerciseId: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.get(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/results`, { params: options, observe: 'response' });
    }
}
