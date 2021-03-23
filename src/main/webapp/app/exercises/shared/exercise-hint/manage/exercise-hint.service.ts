import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { ExerciseHint } from 'app/entities/exercise-hint.model';

export type ExerciseHintResponse = HttpResponse<ExerciseHint>;

export interface IExerciseHintService {
    /**
     * Creates an exercise hint
     * @param exerciseHint Exercise hint to create
     */
    create(exerciseHint: ExerciseHint): Observable<ExerciseHintResponse>;

    /**
     * Updates an exercise hint
     * @param exerciseHint Exercise hint to update
     */
    update(exerciseHint: ExerciseHint): Observable<ExerciseHintResponse>;

    /**
     * Finds an exercise hint
     * @param id Id of exercise hint to find
     */
    find(id: number): Observable<ExerciseHintResponse>;

    /**
     * Finds all exercise hints by exercise id
     * @param exerciseId Id of exercise
     */
    findByExerciseId(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>>;

    /**
     * Deletes an exercise hint
     * @param id Id of exercise hint to delete
     */
    delete(id: number): Observable<HttpResponse<any>>;
}

@Injectable({ providedIn: 'root' })
export class ExerciseHintService implements IExerciseHintService {
    public resourceUrl = SERVER_API_URL + 'api/exercise-hints';

    constructor(protected http: HttpClient) {}

    /**
     * Creates an exercise hint
     * @param exerciseHint Exercise hint to create
     */
    create(exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        return this.http.post<ExerciseHint>(this.resourceUrl, exerciseHint, { observe: 'response' });
    }

    /**
     * Updates an exercise hint
     * @param exerciseHint Exercise hint to update
     */
    update(exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        return this.http.put<ExerciseHint>(`${this.resourceUrl}/${exerciseHint.id}`, exerciseHint, { observe: 'response' });
    }

    /**
     * Finds an exercise hint
     * @param id Id of exercise hint to find
     */
    find(id: number): Observable<ExerciseHintResponse> {
        return this.http.get<ExerciseHint>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    /**
     * Finds all exercise hints by exercise id
     * @param exerciseId Id of exercise
     */
    findByExerciseId(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return this.http.get<ExerciseHint[]>(`api/exercises/${exerciseId}/hints`, { observe: 'response' });
    }

    /**
     * Returns the title of the hint with the given id
     *
     * @param hintId the id of the hint
     * @return the name/title of the hint
     */
    getTitle(hintId: number): Observable<HttpResponse<Map<string, string>>> {
        return this.http.get<Map<string, string>>(`${this.resourceUrl}/${hintId}/get-title`, { observe: 'response' });
    }

    /**
     * Deletes an exercise hint
     * @param id Id of exercise hint to delete
     */
    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
