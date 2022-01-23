import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TextHint } from 'app/entities/hestia/text-hint-model';

export type TextHintResponse = HttpResponse<TextHint>;

export interface ITextHintService {
    /**
     * Creates a text hint
     * @param textHint Text hint to create
     */
    create(textHint: TextHint): Observable<TextHintResponse>;

    /**
     * Updates a text hint
     * @param textHint Text hint to update
     */
    update(textHint: TextHint): Observable<TextHintResponse>;

    /**
     * Finds a text hint
     * @param id Id of text hint to find
     */
    find(id: number): Observable<TextHintResponse>;

    /**
     * Finds all text hints by exercise id
     * @param exerciseId Id of exercise
     */
    findByExerciseId(exerciseId: number): Observable<HttpResponse<TextHint[]>>;

    /**
     * Deletes an exercise hint
     * @param id Id of exercise hint to delete
     */
    delete(id: number): Observable<HttpResponse<void>>;
}

@Injectable({ providedIn: 'root' })
export class TextHintService implements ITextHintService {
    public resourceUrl = SERVER_API_URL + 'api/text-hints';

    constructor(protected http: HttpClient) {}

    /**
     * Creates a text hint
     * @param textHint Text hint to create
     */
    create(textHint: TextHint): Observable<TextHintResponse> {
        textHint.exercise = ExerciseService.convertDateFromClient(textHint.exercise!);
        textHint.type = 'text';
        if (textHint.exercise.categories) {
            textHint.exercise.categories = ExerciseService.stringifyExerciseCategories(textHint.exercise);
        }
        return this.http.post<TextHint>(this.resourceUrl, textHint, { observe: 'response' });
    }

    /**
     * Updates a text hint
     * @param textHint Text hint to update
     */
    update(textHint: TextHint): Observable<TextHintResponse> {
        textHint.exercise = ExerciseService.convertDateFromClient(textHint.exercise!);
        textHint.exercise.categories = ExerciseService.stringifyExerciseCategories(textHint.exercise);
        return this.http.put<TextHint>(`${this.resourceUrl}/${textHint.id}`, textHint, { observe: 'response' });
    }

    /**
     * Finds an text hint
     * @param textHintId Id of text hint to find
     */
    find(textHintId: number): Observable<TextHintResponse> {
        return this.http.get<TextHint>(`${this.resourceUrl}/${textHintId}`, { observe: 'response' });
    }

    /**
     * Finds all text hints by exercise id
     * @param exerciseId Id of exercise
     */
    findByExerciseId(exerciseId: number): Observable<HttpResponse<TextHint[]>> {
        return this.http.get<TextHint[]>(`api/exercises/${exerciseId}/text-hints`, { observe: 'response' });
    }

    /**
     * Fetches the title of the text hint with the given id
     *
     * @param textHintId the id of the hint
     * @return the title of the hint in an HttpResponse, or an HttpErrorResponse on error
     */
    getTitle(textHintId: number): Observable<HttpResponse<string>> {
        return this.http.get(`${this.resourceUrl}/${textHintId}/title`, { observe: 'response', responseType: 'text' });
    }

    /**
     * Deletes a text hint
     * @param textHintId Id of text hint to delete
     */
    delete(textHintId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${textHintId}`, { observe: 'response' });
    }
}
