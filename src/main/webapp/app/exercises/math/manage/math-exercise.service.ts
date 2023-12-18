import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { MathExercise } from 'app/entities/math-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseServicable, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

type EntityResponseType = HttpResponse<MathExercise>;
type EntityArrayResponseType = HttpResponse<MathExercise[]>;

@Injectable({ providedIn: 'root' })
export class MathExerciseService implements ExerciseServicable<MathExercise> {
    private resourceUrl = 'api/math-exercises';

    constructor(
        private http: HttpClient,
        private exerciseService: ExerciseService,
    ) {}

    /**
     * Store a new math exercise on the server.
     * @param mathExercise that should be stored of type {MathExercise}
     */
    create(mathExercise: MathExercise): Observable<EntityResponseType> {
        let copy = ExerciseService.convertExerciseDatesFromClient(mathExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<MathExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Imports a math exercise by cloning the entity itself plus example solutions and example submissions
     *
     * @param adaptedSourceMathExercise The exercise that should be imported, including adapted values for the
     * new exercise. E.g. with another title than the original exercise. Old values that should get discarded
     * (like the old ID) will be handled by the server.
     */
    import(adaptedSourceMathExercise: MathExercise) {
        let copy = ExerciseService.convertExerciseDatesFromClient(adaptedSourceMathExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<MathExercise>(`${this.resourceUrl}/import/${adaptedSourceMathExercise.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Updates an existing math exercise.
     * @param mathExercise that should be updated of type {MathExercise}
     * @param req optional request options
     */
    update(mathExercise: MathExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = ExerciseService.convertExerciseDatesFromClient(mathExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<MathExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Finds the math exercise of the given exerciseId.
     * @param exerciseId of math exercise of type {number}
     * @param withPlagiarismDetectionConfig true if plagiarism detection context should be fetched with the exercise
     */
    find(exerciseId: number, withPlagiarismDetectionConfig: boolean = false): Observable<EntityResponseType> {
        return this.http
            .get<MathExercise>(`${this.resourceUrl}/${exerciseId}`, {
                observe: 'response',
                params: { withPlagiarismDetectionConfig: withPlagiarismDetectionConfig },
            })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Queries all math exercises for the given request options.
     * @param req optional request options
     */
    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<MathExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.processExerciseEntityArrayResponse(res)));
    }

    /**
     * Deletes the math exercise with the given id.
     * @param exerciseId of the math exercise of type {number}
     */
    delete(exerciseId: number): Observable<HttpResponse<any>> {
        return this.http.delete(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' });
    }

    /**
     * Re-evaluates and updates an existing math exercise.
     *
     * @param mathExercise that should be updated of type {MathExercise}
     * @param req optional request options
     */
    reevaluateAndUpdate(mathExercise: MathExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = ExerciseService.convertExerciseDatesFromClient(mathExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<MathExercise>(`${this.resourceUrl}/${mathExercise.id}/re-evaluate`, copy, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }
}
