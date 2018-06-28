import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { Question } from './question.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<Question>;

@Injectable()
export class QuestionService {

    private resourceUrl =  SERVER_API_URL + 'api/questions';

    constructor(private http: HttpClient) { }

    create(question: Question): Observable<EntityResponseType> {
        const copy = this.convert(question);
        return this.http.post<Question>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(question: Question): Observable<EntityResponseType> {
        const copy = this.convert(question);
        return this.http.put<Question>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Question>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<Question[]>> {
        const options = createRequestOption(req);
        return this.http.get<Question[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<Question[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Question = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<Question[]>): HttpResponse<Question[]> {
        const jsonResponse: Question[] = res.body;
        const body: Question[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Question.
     */
    private convertItemFromServer(question: Question): Question {
        const copy: Question = Object.assign({}, question);
        return copy;
    }

    /**
     * Convert a Question to a JSON which can be sent to the server.
     */
    private convert(question: Question): Question {
        const copy: Question = Object.assign({}, question);
        return copy;
    }
}
