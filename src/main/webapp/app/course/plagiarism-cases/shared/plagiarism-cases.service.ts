import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { PlagiarismSubmissionElement } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmissionElement';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';

export type EntityResponseType = HttpResponse<PlagiarismCase>;
export type EntityArrayResponseType = HttpResponse<PlagiarismCase[]>;
export type StatementEntityResponseType = HttpResponse<string>;

@Injectable({ providedIn: 'root' })
export class PlagiarismCasesService {
    private resourceUrl = SERVER_API_URL + 'api/courses';
    private resourceUrlExercises = SERVER_API_URL + 'api/exercises';

    constructor(private http: HttpClient) {}

    /* Instructor */

    /**
     * Get all plagiarism cases for the instructor of the course with the given id
     * @param { number } courseId id of the course
     */
    public getPlagiarismCasesForInstructor(courseId: number): Observable<EntityArrayResponseType> {
        return this.http.get<PlagiarismCase[]>(`${this.resourceUrl}/${courseId}/plagiarism-cases/for-instructor`, { observe: 'response' });
    }

    /**
     * Get the plagiarism case with the given id for the instructor
     * @param { number } courseId id of the course
     * @param { number } plagiarismCaseId id of the plagiarismCase
     */
    public getPlagiarismCaseDetailForInstructor(courseId: number, plagiarismCaseId: number): Observable<EntityResponseType> {
        return this.http.get<PlagiarismCase>(`${this.resourceUrl}/${courseId}/plagiarism-cases/${plagiarismCaseId}/for-instructor`, { observe: 'response' });
    }

    /**
     *
     * @param { number } courseId id of the course
     * @param { number } plagiarismCaseId id of the plagiarismCase
     * @param plagiarismVerdict plagiarism case verdict to save including the verdict itself and optionally the message or the point deduction
     */
    public savePlagiarismCaseVerdict(
        courseId: number,
        plagiarismCaseId: number,
        plagiarismVerdict: { verdict: PlagiarismVerdict; verdictMessage?: string; verdictPointDeduction?: number },
    ): Observable<EntityResponseType> {
        return this.http.put<PlagiarismCase>(`${this.resourceUrl}/${courseId}/plagiarism-cases/${plagiarismCaseId}/verdict`, plagiarismVerdict, { observe: 'response' });
    }

    /* Student */

    /**
     * Get all plagiarism cases for the student of the course with the given id
     * @param { number } courseId id of the course
     * @param { number } exerciseId id of the exercise
     */
    public getPlagiarismCaseForStudent(courseId: number, exerciseId: number): Observable<EntityResponseType> {
        return this.http.get<PlagiarismCase>(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/plagiarism-case`, { observe: 'response' });
    }

    /**
     * Get the plagiarism case with the given id for the student
     * @param { number } courseId id of the course
     * @param { number } plagiarismCaseId id of the plagiarismCase
     */
    public getPlagiarismCaseDetailForStudent(courseId: number, plagiarismCaseId: number): Observable<EntityResponseType> {
        return this.http.get<PlagiarismCase>(`${this.resourceUrl}/${courseId}/plagiarism-cases/${plagiarismCaseId}/for-student`, { observe: 'response' });
    }

    /**
     * Get the plagiarism comparison with the given id
     * @param { number } courseId
     * @param { number } plagiarismComparisonId
     * @param { string } studentLogin
     */
    public getPlagiarismComparisonForSplitView(
        courseId: number,
        plagiarismComparisonId: number,
        studentLogin = '',
    ): Observable<HttpResponse<PlagiarismComparison<PlagiarismSubmissionElement>>> {
        return this.http.get<PlagiarismComparison<PlagiarismSubmissionElement>>(
            `${this.resourceUrl}/${courseId}/plagiarism-comparisons/${plagiarismComparisonId}/for-split-view` + (studentLogin ? `?studentLogin=${studentLogin}` : ''),
            {
                observe: 'response',
            },
        );
    }

    /**
     * Update the status of the plagiarism comparison with given id
     * @param { number } courseId
     * @param { number } plagiarismComparisonId
     * @param { PlagiarismStatus } status
     */
    public updatePlagiarismComparisonStatus(courseId: number, plagiarismComparisonId: number, status: PlagiarismStatus): Observable<HttpResponse<void>> {
        return this.http.put<void>(`${this.resourceUrl}/${courseId}/plagiarism-comparisons/${plagiarismComparisonId}/status`, { status }, { observe: 'response' });
    }

    /**
     * Clean up plagiarism results and comparisons
     * If deleteAll is set to true, all plagiarism results belonging to the exercise are deleted,
     * otherwise only plagiarism comparisons or with status DENIED or CONFIRMED are deleted and old results are deleted as well.
     *
     * @param { number } exerciseId
     * @param {number} plagiarismResultId
     * @param { boolean } deleteAll
     */
    public cleanUpPlagiarism(exerciseId: number, plagiarismResultId: number, deleteAll = false): Observable<HttpResponse<void>> {
        const params = new HttpParams().append('deleteAll', deleteAll ? 'true' : 'false');
        return this.http.delete<void>(`${this.resourceUrlExercises}/${exerciseId}/plagiarism-results/${plagiarismResultId}/plagiarism-comparisons`, {
            params,
            observe: 'response',
        });
    }
}
