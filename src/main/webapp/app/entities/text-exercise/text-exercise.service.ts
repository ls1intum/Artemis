import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';

import { TextExercise } from './text-exercise.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { ExerciseService } from 'app/entities/exercise/exercise.service';

export type EntityResponseType = HttpResponse<TextExercise>;
export type EntityArrayResponseType = HttpResponse<TextExercise[]>;

@Injectable({ providedIn: 'root' })
export class TextExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/text-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    create(textExercise: TextExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(textExercise);
        return this.http
            .post<TextExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    update(textExercise: TextExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.exerciseService.convertDateFromClient(textExercise);
        return this.http
            .put<TextExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<TextExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<TextExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
