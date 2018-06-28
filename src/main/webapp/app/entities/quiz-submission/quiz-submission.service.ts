import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { QuizSubmission } from './quiz-submission.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<QuizSubmission>;

@Injectable()
export class QuizSubmissionService {

    private resourceUrl =  SERVER_API_URL + 'api/quiz-submissions';

    constructor(private http: HttpClient) { }

    create(quizSubmission: QuizSubmission): Observable<EntityResponseType> {
        const copy = this.convert(quizSubmission);
        return this.http.post<QuizSubmission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(quizSubmission: QuizSubmission): Observable<EntityResponseType> {
        const copy = this.convert(quizSubmission);
        return this.http.put<QuizSubmission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<QuizSubmission>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<QuizSubmission[]>> {
        const options = createRequestOption(req);
        return this.http.get<QuizSubmission[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<QuizSubmission[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: QuizSubmission = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<QuizSubmission[]>): HttpResponse<QuizSubmission[]> {
        const jsonResponse: QuizSubmission[] = res.body;
        const body: QuizSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to QuizSubmission.
     */
    private convertItemFromServer(quizSubmission: QuizSubmission): QuizSubmission {
        const copy: QuizSubmission = Object.assign({}, quizSubmission);
        return copy;
    }

    /**
     * Convert a QuizSubmission to a JSON which can be sent to the server.
     */
    private convert(quizSubmission: QuizSubmission): QuizSubmission {
        const copy: QuizSubmission = Object.assign({}, quizSubmission);
        return copy;
    }
}
