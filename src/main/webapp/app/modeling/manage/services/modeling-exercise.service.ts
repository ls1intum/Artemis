import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { ModelingExercise, toUpdateModelingExerciseDTO } from 'app/modeling/shared/entities/modeling-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseServicable, ExerciseService } from 'app/exercise/services/exercise.service';
import { downloadStream } from 'app/shared/util/download.util';

export type EntityResponseType = HttpResponse<ModelingExercise>;
export type EntityArrayResponseType = HttpResponse<ModelingExercise[]>;

@Injectable({ providedIn: 'root' })
export class ModelingExerciseService implements ExerciseServicable<ModelingExercise> {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);

    public resourceUrl = 'api/modeling/modeling-exercises';
    public adminResourceUrl = 'api/modeling/admin/modeling-exercises';

    create(modelingExercise: ModelingExercise): Observable<EntityResponseType> {
        let copy = ExerciseService.convertExerciseDatesFromClient(modelingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<ModelingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    update(modelingExercise: ModelingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const dto = toUpdateModelingExerciseDTO(modelingExercise);
        return this.http
            .put<ModelingExercise>(this.resourceUrl, dto, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    find(modelingExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ModelingExercise>(`${this.resourceUrl}/${modelingExerciseId}`, { observe: 'response' })
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
    import(adaptedSourceModelingExercise: ModelingExercise) {
        let copy = ExerciseService.convertExerciseDatesFromClient(adaptedSourceModelingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<ModelingExercise>(`${this.resourceUrl}/import/${adaptedSourceModelingExercise.id}`, copy, { observe: 'response' })
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
    reevaluateAndUpdate(modelingExercise: ModelingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const dto = toUpdateModelingExerciseDTO(modelingExercise);
        return this.http
            .put<ModelingExercise>(`${this.resourceUrl}/${modelingExercise.id}/re-evaluate`, dto, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }
}
