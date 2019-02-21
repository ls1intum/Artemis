import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { ModelingExercise } from './modeling-exercise.model';
import { ExerciseService } from 'app/entities/exercise';
import { ModelingStatistic } from 'app/entities/modeling-statistic';

export type EntityResponseType = HttpResponse<ModelingExercise>;
export type EntityArrayResponseType = HttpResponse<ModelingExercise[]>;

@Injectable({ providedIn: 'root' })
export class ModelingExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/modeling-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {
        this.exerciseService = exerciseService;
    }

    create(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(modelingExercise);
        return this.http
            .post<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    update(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(modelingExercise);
        return this.http
            .put<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<ModelingExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    getStatistics(id: number): Observable<HttpResponse<ModelingStatistic>> {
        return this.http.get<ModelingStatistic>(`${this.resourceUrl}/${id}/statistics`, { observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
