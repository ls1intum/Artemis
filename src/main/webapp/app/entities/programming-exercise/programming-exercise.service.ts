import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { ProgrammingExercise } from './programming-exercise.model';
import { createRequestOption } from '../../shared';
import * as moment from 'moment';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
export type EntityArrayResponseType = HttpResponse<ProgrammingExercise[]>;

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient) {}

    create(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(programmingExercise);
        return this.http
            .post<ProgrammingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    automaticSetup(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(programmingExercise);
        return this.http
            .post<ProgrammingExercise>(this.resourceUrl + '/setup', copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(programmingExercise);
        return this.http
            .put<ProgrammingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<ProgrammingExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<ProgrammingExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res));
    }

    delete(id: number, deleteStudentReposBuildPlans: boolean, deleteBaseReposBuildPlans: boolean): Observable<HttpResponse<void>> {
        let params = new HttpParams();
        params = params.set('deleteStudentReposBuildPlans', deleteStudentReposBuildPlans.toString());
        params = params.set('deleteBaseReposBuildPlans', deleteBaseReposBuildPlans.toString());
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { params, observe: 'response' });
    }

    protected convertDateFromClient(exercise: ProgrammingExercise): ProgrammingExercise {
        const copy: ProgrammingExercise = Object.assign({}, exercise, {
            releaseDate: exercise.releaseDate != null && moment(exercise.releaseDate).isValid() ? exercise.releaseDate.toJSON() : null,
            dueDate: exercise.dueDate != null && moment(exercise.dueDate).isValid() ? exercise.dueDate.toJSON() : null
        });
        return copy;
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate != null ? moment(res.body.releaseDate) : null;
            res.body.dueDate = res.body.dueDate != null ? moment(res.body.dueDate) : null;
        }
        return res;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((exercise: ProgrammingExercise) => {
                exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
                exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
            });
        }
        return res;
    }
}
