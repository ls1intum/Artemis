import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface IProgrammingExerciseSolutionEntryService {
    /**
     * Deletes a programming exercise solution entry
     * @param exerciseId of the programming exercise
     * @param codeHintId of the code hint
     * @param solutionEntryId of the solution entry to be deleted
     */
    deleteSolutionEntry(exerciseId: number, codeHintId: number, solutionEntryId: number): Observable<HttpResponse<void>>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSolutionEntryService implements IProgrammingExerciseSolutionEntryService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(protected http: HttpClient) {}

    /**
     * Deletes a programming exercise solution entry
     * @param exerciseId of the programming exercise
     * @param codeHintId of the code hint
     * @param solutionEntryId of the solution entry to be deleted
     */
    deleteSolutionEntry(exerciseId: number, codeHintId: number, solutionEntryId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/code-hints/${codeHintId}/solution-entries/${solutionEntryId}`, { observe: 'response' });
    }
}
