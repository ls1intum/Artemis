import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { DragAndDropQuestion } from './drag-and-drop-question.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<DragAndDropQuestion>;

@Injectable()
export class DragAndDropQuestionService {

    private resourceUrl =  SERVER_API_URL + 'api/drag-and-drop-questions';

    constructor(private http: HttpClient) { }

    create(dragAndDropQuestion: DragAndDropQuestion): Observable<EntityResponseType> {
        const copy = this.convert(dragAndDropQuestion);
        return this.http.post<DragAndDropQuestion>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(dragAndDropQuestion: DragAndDropQuestion): Observable<EntityResponseType> {
        const copy = this.convert(dragAndDropQuestion);
        return this.http.put<DragAndDropQuestion>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<DragAndDropQuestion>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<DragAndDropQuestion[]>> {
        const options = createRequestOption(req);
        return this.http.get<DragAndDropQuestion[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<DragAndDropQuestion[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: DragAndDropQuestion = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<DragAndDropQuestion[]>): HttpResponse<DragAndDropQuestion[]> {
        const jsonResponse: DragAndDropQuestion[] = res.body;
        const body: DragAndDropQuestion[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to DragAndDropQuestion.
     */
    private convertItemFromServer(dragAndDropQuestion: DragAndDropQuestion): DragAndDropQuestion {
        const copy: DragAndDropQuestion = Object.assign({}, dragAndDropQuestion);
        return copy;
    }

    /**
     * Convert a DragAndDropQuestion to a JSON which can be sent to the server.
     */
    private convert(dragAndDropQuestion: DragAndDropQuestion): DragAndDropQuestion {
        const copy: DragAndDropQuestion = Object.assign({}, dragAndDropQuestion);
        return copy;
    }
}
