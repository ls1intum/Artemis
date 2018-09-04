import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { ModelingExercise } from './modeling-exercise.model';
import { createRequestOption } from '../../shared';
import { JhiDateUtils } from 'ng-jhipster';

export type EntityResponseType = HttpResponse<ModelingExercise>;

@Injectable()
export class ModelingExerciseService {

    private resourceUrl =  SERVER_API_URL + 'api/modeling-exercises';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    create(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        const copy = this.convert(modelingExercise);
        return this.http.post<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        const copy = this.convert(modelingExercise);
        return this.http.put<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ModelingExercise>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<ModelingExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<ModelingExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<ModelingExercise[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ModelingExercise = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<ModelingExercise[]>): HttpResponse<ModelingExercise[]> {
        const jsonResponse: ModelingExercise[] = res.body;
        const body: ModelingExercise[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to ModelingExercise.
     */
    private convertItemFromServer(modelingExercise: ModelingExercise): ModelingExercise {
        const copy: ModelingExercise = Object.assign({}, modelingExercise);
        copy.releaseDate = this.dateUtils.convertDateTimeFromServer(copy.releaseDate);
        copy.dueDate = this.dateUtils.convertDateTimeFromServer(copy.dueDate);
        return copy;
    }

    /**
     * Convert a ModelingExercise to a JSON which can be sent to the server.
     */
    private convert(modelingExercise: ModelingExercise): ModelingExercise {
        const copy: ModelingExercise = Object.assign({}, modelingExercise);
        copy.releaseDate = this.dateUtils.toDate(modelingExercise.releaseDate);
        copy.dueDate = this.dateUtils.toDate(modelingExercise.dueDate);
        return copy;
    }
}
