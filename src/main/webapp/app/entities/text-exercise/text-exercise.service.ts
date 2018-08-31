import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { TextExercise } from './text-exercise.model';
import { createRequestOption } from '../../shared';
import { JhiDateUtils } from 'ng-jhipster';

export type EntityResponseType = HttpResponse<TextExercise>;

@Injectable()
export class TextExerciseService {

    private resourceUrl =  SERVER_API_URL + 'api/text-exercises';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    create(textExercise: TextExercise): Observable<EntityResponseType> {
        const copy = this.convert(textExercise);
        return this.http.post<TextExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(textExercise: TextExercise): Observable<EntityResponseType> {
        const copy = this.convert(textExercise);
        return this.http.put<TextExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<TextExercise>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<TextExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<TextExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<TextExercise[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: TextExercise = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<TextExercise[]>): HttpResponse<TextExercise[]> {
        const jsonResponse: TextExercise[] = res.body;
        const body: TextExercise[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to TextExercise.
     */
    private convertItemFromServer(textExercise: TextExercise): TextExercise {
        const copy: TextExercise = Object.assign({}, textExercise);
        copy.releaseDate = this.dateUtils.convertDateTimeFromServer(copy.releaseDate);
        copy.dueDate = this.dateUtils.convertDateTimeFromServer(copy.dueDate);
        return copy;
    }

    /**
     * Convert a TextExercise to a JSON which can be sent to the server.
     */
    private convert(textExercise: TextExercise): TextExercise {
        const copy: TextExercise = Object.assign({}, textExercise);
        copy.releaseDate = this.dateUtils.toDate(textExercise.releaseDate);
        copy.dueDate = this.dateUtils.toDate(textExercise.dueDate);
        return copy;
    }
}
