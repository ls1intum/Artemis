import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import * as moment from 'moment';

import { ModelingExercise } from './modeling-exercise.model';
import { createRequestOption } from '../../shared';
import { EntityArrayResponseType, Exercise } from 'app/entities/exercise';

export type EntityResponseType = HttpResponse<ModelingExercise>;
export type EntityArrayResponseType = HttpResponse<ModelingExercise[]>;

@Injectable()
export class ModelingExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/modeling-exercises';

    constructor(private http: HttpClient) {}

    create(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(modelingExercise);
        return this.http
            .post<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(modelingExercise);
        return this.http
            .put<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<ModelingExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<ModelingExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertDateFromClient(exercise: ModelingExercise): ModelingExercise {
        const copy: ModelingExercise = Object.assign({}, exercise, {
            releaseDate: exercise.releaseDate != null && moment(exercise.releaseDate).isValid() ? exercise.releaseDate.toJSON() : null,
            dueDate: exercise.dueDate != null && moment(exercise.dueDate).isValid() ? exercise.dueDate.toJSON() : null
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.releaseDate = res.body.releaseDate != null ? moment(res.body.releaseDate) : null;
        res.body.dueDate = res.body.dueDate != null ? moment(res.body.dueDate) : null;
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((exercise: Exercise) => {
            exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
            exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
        });
        return res;
    }
}
