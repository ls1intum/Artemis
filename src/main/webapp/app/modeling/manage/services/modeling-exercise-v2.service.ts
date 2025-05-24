import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { ModelingExerciseV2 } from 'app/modeling/shared/entities/modeling-exercise-v2.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseServicable, ExerciseService } from 'app/exercise/services/exercise.service';
import { downloadStream } from 'app/shared/util/download.util';

export type EntityResponseType = HttpResponse<ModelingExerciseV2>;
export type EntityArrayResponseType = HttpResponse<ModelingExerciseV2[]>;

@Injectable({ providedIn: 'root' })
export class ModelingExerciseV2Service implements ExerciseServicable<ModelingExerciseV2> {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);

    public resourceUrl = 'api/modeling/modeling-exercises';
    public adminResourceUrl = 'api/modeling/admin/modeling-exercises';

    create(modelingExercise: ModelingExerciseV2): Observable<EntityResponseType> {
        let copy = ExerciseService.convertExerciseDatesFromClient(modelingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<ModelingExerciseV2>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    update(modelingExercise: ModelingExerciseV2, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = ExerciseService.convertExerciseDatesFromClient(modelingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<ModelingExerciseV2>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    find(modelingExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ModelingExerciseV2>(`${this.resourceUrl}/${modelingExerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    delete(modelingExerciseId: number): Observable<HttpResponse<any>> {
        return this.http.delete(`${this.resourceUrl}/${modelingExerciseId}`, { observe: 'response' });
    }

    /**
     * Imports a modeling exercise by cloning the entity itself plus example solutions and example submissions
     *
     * @param adaptedSourceModelingExercise The exercise that should be imported, including adapted values for the
     * new exercise. E.g. with another title than the original exercise. Old values that should get discarded
     * (like the old ID) will be handled by the server.
     */
    import(adaptedSourceModelingExercise: ModelingExerciseV2) {
        let copy = ExerciseService.convertExerciseDatesFromClient(adaptedSourceModelingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<ModelingExerciseV2>(`${this.resourceUrl}/import/${adaptedSourceModelingExercise.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    convertToPdf(model: string, filename: string): Observable<HttpResponse<Blob>> {
        return this.http
            .post('api/modeling/apollon/convert-to-pdf', { model }, { observe: 'response', responseType: 'blob' })
            .pipe(tap((response: HttpResponse<Blob>) => downloadStream(response.body, 'application/pdf', filename)));
    }

    /**
     * Re-evaluates and updates a modeling exercise.
     *
     * @param modelingExercise that should be updated of type {ModelingExercise}
     * @param req optional request options
     */
    reevaluateAndUpdate(modelingExercise: ModelingExerciseV2, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = ExerciseService.convertExerciseDatesFromClient(modelingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<ModelingExerciseV2>(`${this.resourceUrl}/${modelingExercise.id}/re-evaluate`, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }
}
