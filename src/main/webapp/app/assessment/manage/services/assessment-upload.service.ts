import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * The different validation errors that the server can report for a manual assessment upload.
 * Mirrors the server enum {@code AssessmentUploadErrorType}.
 */
export type AssessmentUploadErrorType =
    | 'MISSING_CSV'
    | 'MULTIPLE_CSV'
    | 'EMPTY_CSV'
    | 'MALFORMED_CSV'
    | 'MISSING_OVERALL_POINTS_COLUMN'
    | 'MISSING_IDENTIFIER'
    | 'INVALID_IDENTIFIER_FORMAT'
    | 'DUPLICATE_IDENTIFIER'
    | 'PARTICIPATION_NOT_FOUND'
    | 'PARTICIPATION_WRONG_EXERCISE'
    | 'IDENTIFIER_MISMATCH'
    | 'EXISTING_COMPLAINT'
    | 'INVALID_POINTS'
    | 'DUPLICATE_TEXT_FILE'
    | 'AMBIGUOUS_TEXT_FILE'
    | 'MISSING_TEXT_FILE'
    | 'UNMATCHED_TEXT_FILE';

/**
 * A single validation error for one CSV row or text file of a manual assessment upload.
 *
 * Invariant: `type` is always present; `identifier` and `detail` are optional.
 */
export interface AssessmentUploadError {
    identifier?: string;
    type: AssessmentUploadErrorType;
    detail?: string;
}

/**
 * The result of a manual assessment upload. If {@link errors} is non-empty the upload was rejected as a whole and nothing was stored.
 *
 * Invariant: `errors` is empty/absent if and only if the upload succeeded. On success `createdStudentIdentifiers` lists the affected participants and `numberOfCreatedAssessments`
 * equals its length; on failure both are empty/absent. (Empty arrays are omitted by the server's `@JsonInclude(NON_EMPTY)`, so treat an absent array as empty.)
 */
export interface AssessmentUploadResult {
    numberOfCreatedAssessments: number;
    createdStudentIdentifiers?: string[];
    errors?: AssessmentUploadError[];
}

@Injectable({ providedIn: 'root' })
export class AssessmentUploadService {
    private readonly http = inject(HttpClient);

    private readonly resourceUrl = 'api/assessment/exercises';

    /**
     * Uploads a zip file containing manual assessments (an `assessment-scores.csv` and one `.txt` file per participant) for a programming exercise.
     *
     * Precondition: `exerciseId` refers to a programming exercise the current user may assess, and `zipFile` is the archive to upload (a `.zip`).
     * Postcondition: issues a single multipart POST and emits the server's result once; performs no client-side state change and does not mutate its arguments.
     *
     * @param exerciseId the id of the programming exercise
     * @param zipFile the zip file to upload
     * @return an observable emitting the parsing/storing result reported by the server
     */
    uploadManualAssessments(exerciseId: number, zipFile: File): Observable<HttpResponse<AssessmentUploadResult>> {
        const formData = new FormData();
        formData.append('file', zipFile);
        return this.http.post<AssessmentUploadResult>(`${this.resourceUrl}/${exerciseId}/manual-assessments`, formData, { observe: 'response' });
    }

    /**
     * Downloads a template zip for a programming exercise: an `assessment-scores.csv` pre-filled with every participant's identifier and an empty `Overall points` column, plus one
     * empty `.txt` feedback file per participant. The instructor fills in the points and feedback and uploads the archive via {@link uploadManualAssessments}.
     *
     * Precondition: `exerciseId` refers to a programming exercise the current user may assess.
     * Postcondition: issues a single GET and emits the zip file response once; performs no client-side state change.
     *
     * @param exerciseId the id of the programming exercise
     * @return an observable emitting the template zip response
     */
    downloadTemplate(exerciseId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/manual-assessments/template`, { observe: 'response', responseType: 'blob' });
    }
}
