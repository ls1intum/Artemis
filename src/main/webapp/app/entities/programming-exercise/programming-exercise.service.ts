import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { ProgrammingExercise } from './programming-exercise.model';
import { createRequestOption } from '../../shared';
import { JhiDateUtils } from 'ng-jhipster';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;

@Injectable()
export class ProgrammingExerciseService {

    private resourceUrl =  SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    create(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        const copy = this.convert(programmingExercise);
        return this.http.post<ProgrammingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        const copy = this.convert(programmingExercise);
        return this.http.put<ProgrammingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ProgrammingExercise>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<ProgrammingExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<ProgrammingExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<ProgrammingExercise[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ProgrammingExercise = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<ProgrammingExercise[]>): HttpResponse<ProgrammingExercise[]> {
        const jsonResponse: ProgrammingExercise[] = res.body;
        const body: ProgrammingExercise[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Exercise.
     */
    private convertItemFromServer(programmingExercise: ProgrammingExercise): ProgrammingExercise {
        const entity: ProgrammingExercise = Object.assign({}, programmingExercise);
        entity.releaseDate = this.dateUtils
            .convertDateTimeFromServer(entity.releaseDate);
        entity.dueDate = this.dateUtils
            .convertDateTimeFromServer(entity.dueDate);
        return entity;
    }

    /**
     * Convert a Exercise to a JSON which can be sent to the server.
     */
    private convert(programmingExercise: ProgrammingExercise): ProgrammingExercise {
        const copy: ProgrammingExercise = Object.assign({}, programmingExercise);
        copy.releaseDate = this.dateUtils.toDate(programmingExercise.releaseDate);
        copy.dueDate = this.dateUtils.toDate(programmingExercise.dueDate);
        return copy;
    }
}
