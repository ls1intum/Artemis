import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';

import { ModelingExercise } from '../../../entities/modeling-exercise.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { ModelingStatistic } from 'app/entities/modeling-statistic.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

export type EntityResponseType = HttpResponse<ModelingExercise>;
export type EntityArrayResponseType = HttpResponse<ModelingExercise[]>;

@Injectable({ providedIn: 'root' })
export class ModelingExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/modeling-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {
        this.exerciseService = exerciseService;
    }

    /**
     * creates a ModelingExercise similar to the modelingExercise parameter on the server
     * @param modelingExercise
     */
    create(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(modelingExercise);
        return this.http
            .post<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * updates Modeling Exercise with given modelingExercise parameter
     * @param modelingExercise
     * @param req to copy request params from
     */
    update(modelingExercise: ModelingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.exerciseService.convertDateFromClient(modelingExercise);
        return this.http
            .put<ModelingExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Find ModelingExercise for given modelingExerciseId
     * @param modelingExerciseId
     */
    find(modelingExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ModelingExercise>(`${this.resourceUrl}/${modelingExerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Get Statistics about ModelingExercise
     * @param modelingExerciseId
     */
    getStatistics(modelingExerciseId: number): Observable<HttpResponse<ModelingStatistic>> {
        return this.http.get<ModelingStatistic>(`${this.resourceUrl}/${modelingExerciseId}/statistics`, { observe: 'response' });
    }

    /**
     * delete ModelingExercise
     * @param modelingExerciseId
     */
    delete(modelingExerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${this.resourceUrl}/${modelingExerciseId}`, { observe: 'response' });
    }
}
