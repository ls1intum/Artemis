import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseHint, HintType } from 'app/entities/hestia/exercise-hint.model';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';

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
     * Deletes an exercise hint
     * @param exerciseId Id of the exercise of which to delete the hint
     * @param exerciseHintId Id of exercise hint to delete
     */
    delete(exerciseId: number, exerciseHintId: number): Observable<HttpResponse<void>>;

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
     * Gets all available exercise hints
     * @param exerciseId Id of the exercise of which to retrieve all available exercise hints
     */
    getAvailableExerciseHints(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>>;

    /**
     * Gets all activated exercise hints
     * @param exerciseId Id of the exercise of which to retrieve all activated exercise hints
     */
    getActivatedExerciseHints(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>>;

    /**
     * Activates an exercise hint for the current user
     * @param exerciseId Id of the exercise
     * @param exerciseHintId Id of the exercise hint
     */
    activateExerciseHint(exerciseId: number, exerciseHintId: number): Observable<ExerciseHintResponse>;

    /**
     * Activates an exercise hint for the current user
     * @param exerciseId Id of the exercise
     * @param exerciseHintId Id of the exercise hint
     * @param ratingValue Value of the rating
     */
    rateExerciseHint(exerciseId: number, exerciseHintId: number, ratingValue: number): Observable<HttpResponse<void>>;
}

@Injectable({ providedIn: 'root' })
export class ExerciseHintService implements IExerciseHintService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(protected http: HttpClient, private entityTitleService: EntityTitleService) {}

    /**
     * Creates an exercise hint
     * @param exerciseId of the exercise
     * @param exerciseHint Exercise hint to create
     */
    create(exerciseId: number, exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        exerciseHint.exercise = ExerciseService.convertExerciseDatesFromClient(exerciseHint.exercise!);
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
        exerciseHint.exercise = ExerciseService.convertExerciseDatesFromClient(exerciseHint.exercise!);
        exerciseHint.exercise.categories = ExerciseService.stringifyExerciseCategories(exerciseHint.exercise);
        return this.http.put<ExerciseHint>(`${this.resourceUrl}/${exerciseId}/exercise-hints/${exerciseHint.id}`, exerciseHint, { observe: 'response' });
    }

    /**
     * Deletes an exercise hint
     * @param exerciseId Id of the exercise of which to delete the hint
     * @param exerciseHintId Id of exercise hint to delete
     */
    delete(exerciseId: number, exerciseHintId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/exercise-hints/${exerciseHintId}`, { observe: 'response' });
    }

    /**
     * Finds an exercise hint
     * @param exerciseId Id of the exercise of which to retrieve the hint
     * @param exerciseHintId Id of exercise hint to find
     */
    find(exerciseId: number, exerciseHintId: number): Observable<ExerciseHintResponse> {
        return this.http
            .get<ExerciseHint>(`${this.resourceUrl}/${exerciseId}/exercise-hints/${exerciseHintId}`, { observe: 'response' })
            .pipe(tap((res) => this.sendTitlesToEntityTitleService(res?.body, exerciseId)));
    }

    /**
     * Finds all exercise hints by exercise id
     * Also fetches any relations. This currently only includes the submission entries of a code hint
     * @param exerciseId Id of exercise
     */
    findByExerciseId(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return this.http
            .get<ExerciseHint[]>(`${this.resourceUrl}/${exerciseId}/exercise-hints`, { observe: 'response' })
            .pipe(tap((res) => res?.body?.forEach((hint) => this.sendTitlesToEntityTitleService(hint, exerciseId))));
    }

    /**
     * Gets all available exercise hints
     * @param exerciseId Id of the exercise of which to retrieve all available exercise hints
     */
    getAvailableExerciseHints(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return this.http
            .get<ExerciseHint[]>(`${this.resourceUrl}/${exerciseId}/exercise-hints/available`, { observe: 'response' })
            .pipe(tap((res) => res?.body?.forEach((hint) => this.sendTitlesToEntityTitleService(hint, exerciseId))));
    }

    /**
     * Gets all activated exercise hints
     * @param exerciseId Id of the exercise of which to retrieve all activated exercise hints
     */
    getActivatedExerciseHints(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return this.http
            .get<ExerciseHint[]>(`${this.resourceUrl}/${exerciseId}/exercise-hints/activated`, { observe: 'response' })
            .pipe(tap((res) => res?.body?.forEach((hint) => this.sendTitlesToEntityTitleService(hint, exerciseId))));
    }

    /**
     * Activates an exercise hint for the current user
     * @param exerciseId Id of the exercise
     * @param exerciseHintId Id of the exercise hint
     */
    activateExerciseHint(exerciseId: number, exerciseHintId: number): Observable<ExerciseHintResponse> {
        return this.http.post<ExerciseHint>(`${this.resourceUrl}/${exerciseId}/exercise-hints/${exerciseHintId}/activate`, {}, { observe: 'response' });
    }

    /**
     * Activates an exercise hint for the current user
     * @param exerciseId Id of the exercise
     * @param exerciseHintId Id of the exercise hint
     * @param ratingValue Value of the rating
     */
    rateExerciseHint(exerciseId: number, exerciseHintId: number, ratingValue: number): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/${exerciseId}/exercise-hints/${exerciseHintId}/rating/${ratingValue}`, {}, { observe: 'response' });
    }

    private sendTitlesToEntityTitleService(hint: ExerciseHint | undefined | null, exerciseId: number) {
        this.entityTitleService.setTitle(EntityType.HINT, [hint?.id, exerciseId], hint?.title);
    }
}
