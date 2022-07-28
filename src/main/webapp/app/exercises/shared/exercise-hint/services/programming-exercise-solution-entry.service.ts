import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';

export interface IProgrammingExerciseSolutionEntryService {
    /**
     * Create a custom solution entry for a programming exercise and test case.
     * @param exerciseId of the programming exercise
     * @param testCaseId of the test case that the entry relates to
     * @param entry the entry to be created
     */
    createSolutionEntry(exerciseId: number, testCaseId: number, entry: ProgrammingExerciseSolutionEntry): Observable<ProgrammingExerciseSolutionEntry>;

    /**
     * Get all solution entries for a programming exercise.
     * @param exerciseId of the programming exercise
     */
    getSolutionEntriesForExercise(exerciseId: number): Observable<ProgrammingExerciseSolutionEntry[]>;

    /**
     * Update a solution entry and returns the updated entry.
     * @param exerciseId of the programming exercise
     * @param testCaseId of the test case
     * @param solutionEntryId of the solution entry to update
     * @param entry the entry to update
     */
    updateSolutionEntry(exerciseId: number, testCaseId: number, solutionEntryId: number, entry: ProgrammingExerciseSolutionEntry): Observable<ProgrammingExerciseSolutionEntry>;

    /**
     * Delete a solution entry.
     * @param exerciseId of the programming exercise
     * @param testCaseId of the test case
     * @param solutionEntryId of the solution entry to delete
     */
    deleteSolutionEntry(exerciseId: number, testCaseId: number, solutionEntryId: number): Observable<void>;

    /**
     * Delete all solution entries for a programming exercise.
     * @param exerciseId of the programming exercise
     */
    deleteAllSolutionEntriesForExercise(exerciseId: number): Observable<void>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSolutionEntryService implements IProgrammingExerciseSolutionEntryService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(protected http: HttpClient) {}

    /**
     * Create a custom solution entry for a programming exercise and test case.
     * @param exerciseId of the programming exercise
     * @param testCaseId of the test case that the entry relates to
     * @param entry the entry to be created
     */
    createSolutionEntry(exerciseId: number, testCaseId: number, entry: ProgrammingExerciseSolutionEntry): Observable<ProgrammingExerciseSolutionEntry> {
        return this.http.post<ProgrammingExerciseSolutionEntry>(`${this.resourceUrl}/${exerciseId}/test-cases/${testCaseId}/solution-entries`, entry);
    }

    /**
     * Get all solution entries for a programming exercise.
     * @param exerciseId of the programming exercise
     */
    getSolutionEntriesForExercise(exerciseId: number): Observable<ProgrammingExerciseSolutionEntry[]> {
        return this.http.get<ProgrammingExerciseSolutionEntry[]>(`${this.resourceUrl}/${exerciseId}/solution-entries`);
    }

    /**
     * Update a solution entry and returns the updated entry.
     * @param exerciseId of the programming exercise
     * @param testCaseId of the test case
     * @param solutionEntryId of the solution entry to update
     * @param entry the entry to update
     */
    updateSolutionEntry(exerciseId: number, testCaseId: number, solutionEntryId: number, entry: ProgrammingExerciseSolutionEntry): Observable<ProgrammingExerciseSolutionEntry> {
        return this.http.put<ProgrammingExerciseSolutionEntry>(`${this.resourceUrl}/${exerciseId}/test-cases/${testCaseId}/solution-entries/${solutionEntryId}`, entry);
    }

    /**
     * Delete a solution entry.
     * @param exerciseId of the programming exercise
     * @param testCaseId of the test case
     * @param solutionEntryId of the solution entry to delete
     */
    deleteSolutionEntry(exerciseId: number, testCaseId: number, solutionEntryId: number) {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/test-cases/${testCaseId}/solution-entries/${solutionEntryId}`);
    }

    /**
     * Delete all solution entries for a programming exercise.
     * @param exerciseId of the programming exercise
     */
    deleteAllSolutionEntriesForExercise(exerciseId: number) {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/solution-entries`);
    }
}
