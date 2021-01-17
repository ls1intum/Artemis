import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';

import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { ModelingStatistic } from 'app/entities/modeling-statistic.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { PlagiarismOptions } from 'app/exercises/shared/plagiarism/types/PlagiarismOptions';

export type EntityResponseType = HttpResponse<ModelingExercise>;
export type EntityArrayResponseType = HttpResponse<ModelingExercise[]>;
export type ModelingSubmissionComparisonDTO = {
    element1: ModelingSubmissionComparisonElement;
    element2: ModelingSubmissionComparisonElement;
    confirmed?: boolean;
    similarity: number;
};

export type ModelingSubmissionComparisonElement = {
    submissionId: number;
    size: number;
    score: number;
    studentLogin: string;
    submissionLink: string;
};

@Injectable({ providedIn: 'root' })
export class ModelingExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/modeling-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {
        this.exerciseService = exerciseService;
    }

    create(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        let copy = this.exerciseService.convertDateFromClient(modelingExercise);
        copy = this.exerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        return this.http
            .post<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    update(modelingExercise: ModelingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = this.exerciseService.convertDateFromClient(modelingExercise);
        copy = this.exerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        return this.http
            .put<ModelingExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    find(modelingExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ModelingExercise>(`${this.resourceUrl}/${modelingExerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    getStatistics(modelingExerciseId: number): Observable<HttpResponse<ModelingStatistic>> {
        return this.http.get<ModelingStatistic>(`${this.resourceUrl}/${modelingExerciseId}/statistics`, { observe: 'response' });
    }

    delete(modelingExerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${this.resourceUrl}/${modelingExerciseId}`, { observe: 'response' });
    }

    /**
     * Imports a modeling exercise by cloning the entity itself plus example solutions and example submissions
     *
     * @param adaptedSourceModelingExercise The exercise that should be imported, including adapted values for the
     * new exercise. E.g. with another title than the original exercise. Old values that should get discarded
     * (like the old ID) will be handled by the server.
     */
    import(adaptedSourceModelingExercise: ModelingExercise) {
        return this.http
            .post<ModelingExercise>(`${this.resourceUrl}/import/${adaptedSourceModelingExercise.id}`, adaptedSourceModelingExercise, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Check for plagiarism
     * @param exerciseId of the programming exercise
     * @param options
     */
    checkPlagiarism(exerciseId: number, options?: PlagiarismOptions): Observable<ModelingPlagiarismResult> {
        return this.http
            .get<ModelingPlagiarismResult>(`${this.resourceUrl}/${exerciseId}/check-plagiarism`, { observe: 'response', params: { ...options?.toParams() } })
            .pipe(map((response: HttpResponse<ModelingPlagiarismResult>) => response.body!));
    }
}
