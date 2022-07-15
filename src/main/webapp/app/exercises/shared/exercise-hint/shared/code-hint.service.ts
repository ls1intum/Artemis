import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { createRequestOption } from 'app/shared/util/request.util';

export interface ICodeHintService {
    /**
     * Generates the code hints for a programming exercise
     * @param exerciseId Id of the programming exercise
     * @param deleteOldCodeHints Whether the old code hints should be deleted
     */
    generateCodeHintsForExercise(exerciseId: number, deleteOldCodeHints: boolean): Observable<HttpResponse<CodeHint[]>>;

    /**
     * Removes a programming exercise solution entry from a code hint
     * @param exerciseId of the programming exercise
     * @param codeHintId of the code hint from which the solution entry will be removed
     * @param solutionEntryId of the solution entry to be removed
     */
    removeSolutionEntryFromCodeHint(exerciseId: number, codeHintId: number, solutionEntryId: number): Observable<HttpResponse<void>>;
}

@Injectable({ providedIn: 'root' })
export class CodeHintService implements ICodeHintService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(protected http: HttpClient) {}

    /**
     * Generates the code hints for a programming exercise
     * @param exerciseId Id of the programming exercise
     * @param deleteOldCodeHints Whether the old code hints should be deleted
     */
    generateCodeHintsForExercise(exerciseId: number, deleteOldCodeHints: boolean): Observable<HttpResponse<CodeHint[]>> {
        const options = createRequestOption({ deleteOldCodeHints });
        return this.http.post<CodeHint[]>(`${this.resourceUrl}/${exerciseId}/code-hints`, undefined, {
            params: options,
            observe: 'response',
        });
    }

    /**
     * Removes a programming exercise solution entry from a code hint
     * @param exerciseId of the programming exercise
     * @param codeHintId of the code hint from which the solution entry will be removed
     * @param solutionEntryId of the solution entry to be removed
     */
    removeSolutionEntryFromCodeHint(exerciseId: number, codeHintId: number, solutionEntryId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/code-hints/${codeHintId}/solution-entries/${solutionEntryId}`, { observe: 'response' });
    }
}
