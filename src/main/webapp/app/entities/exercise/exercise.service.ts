import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_FORMAT } from 'app/shared/constants/input.constants';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IExercise } from 'app/shared/model/exercise.model';

type EntityResponseType = HttpResponse<IExercise>;
type EntityArrayResponseType = HttpResponse<IExercise[]>;

@Injectable({ providedIn: 'root' })
export class ExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/exercises';

    constructor(private http: HttpClient) {}

    create(exercise: IExercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exercise);
        return this.http
            .post<IExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(exercise: IExercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exercise);
        return this.http
            .put<IExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<IExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<IExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertDateFromClient(exercise: IExercise): IExercise {
        const copy: IExercise = Object.assign({}, exercise, {
            releaseDate: exercise.releaseDate != null && exercise.releaseDate.isValid() ? exercise.releaseDate.toJSON() : null,
            dueDate: exercise.dueDate != null && exercise.dueDate.isValid() ? exercise.dueDate.toJSON() : null
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.releaseDate = res.body.releaseDate != null ? moment(res.body.releaseDate) : null;
        res.body.dueDate = res.body.dueDate != null ? moment(res.body.dueDate) : null;
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((exercise: IExercise) => {
            exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
            exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
        });
        return res;
    }
}
