import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';

import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { ModelingStatistic } from 'app/entities/modeling-statistic.model';
import { ExerciseServicable, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { PlagiarismOptions } from 'app/exercises/shared/plagiarism/types/PlagiarismOptions';
import { downloadStream } from 'app/shared/util/download.util';

export type EntityResponseType = HttpResponse<ModelingExercise>;
export type EntityArrayResponseType = HttpResponse<ModelingExercise[]>;

@Injectable({ providedIn: 'root' })
export class ModelingExerciseService implements ExerciseServicable<ModelingExercise> {
    public resourceUrl = SERVER_API_URL + 'api/modeling-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {
        this.exerciseService = exerciseService;
    }

    create(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        let copy = this.exerciseService.convertDateFromClient(modelingExercise);
        copy = this.exerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = this.exerciseService.stringifyExerciseCategories(copy);
        return this.http.post<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)),
            map((res: EntityResponseType) => this.exerciseService.convertExerciseCategoriesFromServer(res)),
        );
    }

    update(modelingExercise: ModelingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = this.exerciseService.convertDateFromClient(modelingExercise);
        copy = this.exerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = this.exerciseService.stringifyExerciseCategories(copy);
        return this.http.put<ModelingExercise>(this.resourceUrl, copy, { params: options, observe: 'response' }).pipe(
            map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)),
            map((res: EntityResponseType) => this.exerciseService.convertExerciseCategoriesFromServer(res)),
        );
    }

    find(modelingExerciseId: number): Observable<EntityResponseType> {
        return this.http.get<ModelingExercise>(`${this.resourceUrl}/${modelingExerciseId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)),
            map((res: EntityResponseType) => this.exerciseService.convertExerciseCategoriesFromServer(res)),
            map((res: EntityResponseType) => this.exerciseService.checkPermission(res)),
        );
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
        let copy = this.exerciseService.convertDateFromClient(adaptedSourceModelingExercise);
        copy = this.exerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = this.exerciseService.stringifyExerciseCategories(copy);
        return this.http.post<ModelingExercise>(`${this.resourceUrl}/import/${adaptedSourceModelingExercise.id}`, copy, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)),
            map((res: EntityResponseType) => this.exerciseService.convertExerciseCategoriesFromServer(res)),
        );
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

    convertToPdf(model: string, filename: string): Observable<any> {
        return this.http
            .post(`${SERVER_API_URL}api/apollon-convert/pdf`, { model }, { observe: 'response', responseType: 'blob' })
            .pipe(map((response: HttpResponse<Blob>) => downloadStream(response.body, 'application/pdf', filename)));
    }

    /**
     * Get the latest plagiarism result for the exercise with the given ID.
     *
     * @param exerciseId
     */
    getLatestPlagiarismResult(exerciseId: number): Observable<ModelingPlagiarismResult> {
        return this.http
            .get<ModelingPlagiarismResult>(`${this.resourceUrl}/${exerciseId}/plagiarism-result`, {
                observe: 'response',
            })
            .pipe(map((response: HttpResponse<ModelingPlagiarismResult>) => response.body!));
    }

    /**
     * Get the number of clusters for the exercise with the given ID.
     *
     * @param exerciseId
     */
    getNumberOfClusters(exerciseId: number): Observable<HttpResponse<number>> {
        return this.http.get<number>(`${this.resourceUrl}/${exerciseId}/check-clusters`, {
            observe: 'response',
        });
    }

    /**
     * Build the clusters to use in Compass
     * @param modelingExerciseId id of the exercise to build the clusters for
     */
    buildClusters(modelingExerciseId: number): Observable<{}> {
        return this.http.post(`${this.resourceUrl}/${modelingExerciseId}/trigger-automatic-assessment`, { observe: 'response' });
    }

    /**
     * Delete the clusters used in Compass
     * @param modelingExerciseId id of the exercise to delete the clusters of
     */
    deleteClusters(modelingExerciseId: number): Observable<{}> {
        return this.http.delete(`${this.resourceUrl}/${modelingExerciseId}/clusters`, { observe: 'response' });
    }

    /**
     * Re-evaluates and updates an modeling exercise.
     *
     * @param modelingExercise that should be updated of type {ModelingExercise}
     * @param req optional request options
     */
    reevaluateAndUpdate(modelingExercise: ModelingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = this.exerciseService.convertDateFromClient(modelingExercise);
        copy = this.exerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = this.exerciseService.stringifyExerciseCategories(copy);
        return this.http.put<ModelingExercise>(`${this.resourceUrl}/${modelingExercise.id}/re-evaluate`, copy, { params: options, observe: 'response' }).pipe(
            map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)),
            map((res: EntityResponseType) => this.exerciseService.convertExerciseCategoriesFromServer(res)),
        );
    }
}
