import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

type EntityResponseType = HttpResponse<ExerciseHint>;
type EntityArrayResponseType = HttpResponse<ExerciseHint[]>;

@Injectable({ providedIn: 'root' })
export class ExerciseHintService {
    public resourceUrl = SERVER_API_URL + 'api/exercise-hints';

    constructor(protected http: HttpClient) {}

    create(exerciseHint: ExerciseHint): Observable<EntityResponseType> {
        return this.http.post<ExerciseHint>(this.resourceUrl, exerciseHint, { observe: 'response' });
    }

    update(exerciseHint: ExerciseHint): Observable<EntityResponseType> {
        return this.http.put<ExerciseHint>(`${this.resourceUrl}/${exerciseHint.id}`, exerciseHint, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ExerciseHint>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    findByExerciseId(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return this.http.get<ExerciseHint[]>(`api/exercises/${exerciseId}/hints`, { observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
