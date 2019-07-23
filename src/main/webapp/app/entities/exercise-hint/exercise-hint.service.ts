import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ExerciseHint, IExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

type EntityResponseType = HttpResponse<IExerciseHint>;
type EntityArrayResponseType = HttpResponse<IExerciseHint[]>;

@Injectable({ providedIn: 'root' })
export class ExerciseHintService {
    public resourceUrl = SERVER_API_URL + 'api/exercise-hints';

    constructor(protected http: HttpClient) {}

    create(exerciseHint: IExerciseHint): Observable<EntityResponseType> {
        return this.http.post<IExerciseHint>(this.resourceUrl, exerciseHint, { observe: 'response' });
    }

    update(exerciseHint: IExerciseHint): Observable<EntityResponseType> {
        return this.http.put<IExerciseHint>(`${this.resourceUrl}/${exerciseHint.id}`, exerciseHint, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IExerciseHint>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    findByExerciseId(exerciseId: number): Observable<HttpResponse<IExerciseHint[]>> {
        return this.http.get<IExerciseHint[]>(`api/exercises/${exerciseId}/hints`, { observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
