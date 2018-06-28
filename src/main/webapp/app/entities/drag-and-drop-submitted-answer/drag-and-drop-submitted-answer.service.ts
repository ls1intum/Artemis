import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { DragAndDropSubmittedAnswer } from './drag-and-drop-submitted-answer.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<DragAndDropSubmittedAnswer>;

@Injectable()
export class DragAndDropSubmittedAnswerService {

    private resourceUrl =  SERVER_API_URL + 'api/drag-and-drop-submitted-answers';

    constructor(private http: HttpClient) { }

    create(dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswer): Observable<EntityResponseType> {
        const copy = this.convert(dragAndDropSubmittedAnswer);
        return this.http.post<DragAndDropSubmittedAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswer): Observable<EntityResponseType> {
        const copy = this.convert(dragAndDropSubmittedAnswer);
        return this.http.put<DragAndDropSubmittedAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<DragAndDropSubmittedAnswer>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<DragAndDropSubmittedAnswer[]>> {
        const options = createRequestOption(req);
        return this.http.get<DragAndDropSubmittedAnswer[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<DragAndDropSubmittedAnswer[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: DragAndDropSubmittedAnswer = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<DragAndDropSubmittedAnswer[]>): HttpResponse<DragAndDropSubmittedAnswer[]> {
        const jsonResponse: DragAndDropSubmittedAnswer[] = res.body;
        const body: DragAndDropSubmittedAnswer[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to DragAndDropSubmittedAnswer.
     */
    private convertItemFromServer(dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswer): DragAndDropSubmittedAnswer {
        const copy: DragAndDropSubmittedAnswer = Object.assign({}, dragAndDropSubmittedAnswer);
        return copy;
    }

    /**
     * Convert a DragAndDropSubmittedAnswer to a JSON which can be sent to the server.
     */
    private convert(dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswer): DragAndDropSubmittedAnswer {
        const copy: DragAndDropSubmittedAnswer = Object.assign({}, dragAndDropSubmittedAnswer);
        return copy;
    }
}
