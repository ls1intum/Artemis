import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { AnswerOption } from './answer-option.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<AnswerOption>;

@Injectable()
export class AnswerOptionService {

    private resourceUrl =  SERVER_API_URL + 'api/answer-options';

    constructor(private http: HttpClient) { }

    create(answerOption: AnswerOption): Observable<EntityResponseType> {
        const copy = this.convert(answerOption);
        return this.http.post<AnswerOption>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(answerOption: AnswerOption): Observable<EntityResponseType> {
        const copy = this.convert(answerOption);
        return this.http.put<AnswerOption>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<AnswerOption>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<AnswerOption[]>> {
        const options = createRequestOption(req);
        return this.http.get<AnswerOption[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<AnswerOption[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: AnswerOption = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<AnswerOption[]>): HttpResponse<AnswerOption[]> {
        const jsonResponse: AnswerOption[] = res.body;
        const body: AnswerOption[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to AnswerOption.
     */
    private convertItemFromServer(answerOption: AnswerOption): AnswerOption {
        const copy: AnswerOption = Object.assign({}, answerOption);
        return copy;
    }

    /**
     * Convert a AnswerOption to a JSON which can be sent to the server.
     */
    private convert(answerOption: AnswerOption): AnswerOption {
        const copy: AnswerOption = Object.assign({}, answerOption);
        return copy;
    }
}
