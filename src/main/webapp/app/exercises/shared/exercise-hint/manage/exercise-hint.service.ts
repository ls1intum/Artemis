import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseHint, HintType } from 'app/entities/hestia/exercise-hint.model';

export type ExerciseHintResponse = HttpResponse<ExerciseHint>;

export interface IExerciseHintService {
    /**
     * Creates an exercise hint
     * @param exerciseId of the exercise
     * @param exerciseHint Exercise hint to create
     */
    create(exerciseId: number, exerciseHint: ExerciseHint): Observable<ExerciseHintResponse>;

    /**
     * Updates an exercise hint
     * @param exerciseId of the exercise
     * @param exerciseHint Exercise hint to update
     */
    update(exerciseId: number, exerciseHint: ExerciseHint): Observable<ExerciseHintResponse>;

    /**
     * Finds an exercise hint
     * @param exerciseId Id of the exercise of which to retrieve the hint
     * @param exerciseHintId Id of exercise hint to find
     */
    find(exerciseId: number, exerciseHintId: number): Observable<ExerciseHintResponse>;

    /**
     * Finds all exercise hints by exercise id
     * @param exerciseId Id of exercise
     */
    findByExerciseId(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>>;

    /**
     * Deletes an exercise hint
     * @param exerciseId Id of the exercise of which to delete the hint
     * @param exerciseHintId Id of exercise hint to delete
     */
    delete(exerciseId: number, exerciseHintId: number): Observable<HttpResponse<void>>;
}

@Injectable({ providedIn: 'root' })
export class ExerciseHintService implements IExerciseHintService {
    public resourceUrl = SERVER_API_URL + 'api/exercises';

    constructor(protected http: HttpClient) {}

    /**
     * Creates an exercise hint
     * @param exerciseId of the exercise
     * @param exerciseHint Exercise hint to create
     */
    create(exerciseId: number, exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        exerciseHint.exercise = ExerciseService.convertDateFromClient(exerciseHint.exercise!);
        exerciseHint.type = HintType.TEXT;
        if (exerciseHint.exercise.categories) {
            exerciseHint.exercise.categories = ExerciseService.stringifyExerciseCategories(exerciseHint.exercise);
        }
        return this.http.post<ExerciseHint>(`${this.resourceUrl}/${exerciseId}/exercise-hints`, exerciseHint, { observe: 'response' });
    }

    /**
     * Updates an exercise hint
     * @param exerciseId of the exercise
     * @param exerciseHint Exercise hint to update
     */
    update(exerciseId: number, exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        exerciseHint.exercise = ExerciseService.convertDateFromClient(exerciseHint.exercise!);
        exerciseHint.exercise.categories = ExerciseService.stringifyExerciseCategories(exerciseHint.exercise);
        return this.http.put<ExerciseHint>(`${this.resourceUrl}/${exerciseId}/exercise-hints/${exerciseHint.id}`, exerciseHint, { observe: 'response' });
    }

    /**
     * Finds an exercise hint
     * @param exerciseId Id of the exercise of which to retrieve the hint
     * @param exerciseHintId Id of exercise hint to find
     */
    find(exerciseId: number, exerciseHintId: number): Observable<ExerciseHintResponse> {
        return this.http.get<ExerciseHint>(`${this.resourceUrl}/${exerciseId}/exercise-hints/${exerciseHintId}`, { observe: 'response' });
    }

    /**
     * Finds all exercise hints by exercise id
     * @param exerciseId Id of exercise
     */
    findByExerciseId(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return this.http.get<ExerciseHint[]>(`${this.resourceUrl}/${exerciseId}/exercise-hints`, { observe: 'response' });
    }

    /**
     * Fetches the title of the exercise hint with the given id
     *
     * @param exerciseHintId the id of the hint
     * @param exerciseId Id of the exercise of which to retrieve the hint's title
     * @return the title of the hint in an HttpResponse, or an HttpErrorResponse on error
     */
    getTitle(exerciseId: number, exerciseHintId: number): Observable<HttpResponse<string>> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/exercise-hints/${exerciseHintId}/title`, { observe: 'response', responseType: 'text' });
    }

    /**
     * Deletes an exercise hint
     * @param exerciseId Id of the exercise of which to delete the hint
     * @param exerciseHintId Id of exercise hint to delete
     */
    delete(exerciseId: number, exerciseHintId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/exercise-hints/${exerciseHintId}`, { observe: 'response' });
    }
}
