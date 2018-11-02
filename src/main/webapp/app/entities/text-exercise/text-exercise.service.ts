import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import * as moment from 'moment';

import { TextExercise } from './text-exercise.model';
import { createRequestOption } from '../../shared';
import { Exercise } from 'app/entities/exercise';

export type EntityResponseType = HttpResponse<TextExercise>;
export type EntityArrayResponseType = HttpResponse<TextExercise[]>;

@Injectable()
export class TextExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/text-exercises';

    constructor(private http: HttpClient) {}

    create(textExercise: TextExercise): Observable<EntityResponseType> {
        const copy = Exercise.convertDateFromClient(textExercise);
        return this.http
            .post<TextExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => Exercise.convertDateFromServer(res));
    }

    update(textExercise: TextExercise): Observable<EntityResponseType> {
        const copy = Exercise.convertDateFromClient(textExercise);
        return this.http
            .put<TextExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => Exercise.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<TextExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => Exercise.convertDateFromServer(res));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<TextExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: EntityArrayResponseType) => Exercise.convertDateArrayFromServer(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
