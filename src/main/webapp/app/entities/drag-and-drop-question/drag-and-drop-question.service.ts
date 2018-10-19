import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';

type EntityResponseType = HttpResponse<IDragAndDropQuestion>;
type EntityArrayResponseType = HttpResponse<IDragAndDropQuestion[]>;

@Injectable({ providedIn: 'root' })
export class DragAndDropQuestionService {
    public resourceUrl = SERVER_API_URL + 'api/drag-and-drop-questions';

    constructor(private http: HttpClient) {}

    create(dragAndDropQuestion: IDragAndDropQuestion): Observable<EntityResponseType> {
        return this.http.post<IDragAndDropQuestion>(this.resourceUrl, dragAndDropQuestion, { observe: 'response' });
    }

    update(dragAndDropQuestion: IDragAndDropQuestion): Observable<EntityResponseType> {
        return this.http.put<IDragAndDropQuestion>(this.resourceUrl, dragAndDropQuestion, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IDragAndDropQuestion>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IDragAndDropQuestion[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
