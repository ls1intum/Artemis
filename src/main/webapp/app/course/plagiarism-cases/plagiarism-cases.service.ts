import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { PlagiarismSubmissionElement } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmissionElement';

export type EntityResponseType = HttpResponse<PlagiarismCase>;
export type EntityArrayResponseType = HttpResponse<PlagiarismCase[]>;
export type StatementEntityResponseType = HttpResponse<string>;

@Injectable({ providedIn: 'root' })
export class PlagiarismCasesService {
    private resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) {}

    /**
     * Get all plagiarism cases for the course with the given id
     * @param { number } courseId
     */
    public getConfirmedComparisons(courseId: number): Observable<HttpResponse<PlagiarismComparison<PlagiarismSubmissionElement>[]>> {
        return this.http.get<PlagiarismComparison<PlagiarismSubmissionElement>[]>(`${this.resourceUrl}/${courseId}/plagiarism-cases`, { observe: 'response' });
    }

    /**
     * Get the plagiarism comparison with the given id
     * Anonymizes the submission of the other student
     * @param { number } courseId
     * @param { number } plagiarismComparisonId
     */
    public getPlagiarismComparisonForStudent(courseId: number, plagiarismComparisonId: number): Observable<EntityResponseType> {
        return this.http.get<PlagiarismCase>(`${this.resourceUrl}/${courseId}/plagiarism-comparisons/${plagiarismComparisonId}`, { observe: 'response' });
    }

    /**
     * Get the plagiarism comparison with the given id
     * @param { number } courseId
     * @param { number } plagiarismComparisonId
     */
    public getPlagiarismComparisonForEditor(courseId: number, plagiarismComparisonId: number): Observable<HttpResponse<PlagiarismComparison<PlagiarismSubmissionElement>>> {
        return this.http.get<PlagiarismComparison<PlagiarismSubmissionElement>>(`${this.resourceUrl}/${courseId}/plagiarism-comparisons/${plagiarismComparisonId}/for-editor`, {
            observe: 'response',
        });
    }

    /**
     * Update the instructorStatement for student with the given studentLogin of the plagiarism comparison with given id
     * @param { number } courseId
     * @param { number } plagiarismComparisonId
     * @param { string } studentLogin
     * @param { string } statement
     */
    public saveInstructorStatement(courseId: number, plagiarismComparisonId: number, studentLogin: string, statement: string): Observable<StatementEntityResponseType> {
        return this.http.put<string>(
            `${this.resourceUrl}/${courseId}/plagiarism-comparisons/${plagiarismComparisonId}/instructor-statement/${studentLogin}`,
            { statement },
            { observe: 'response' },
        );
    }

    /**
     * Update the studentStatement of the plagiarism comparison with given id
     * @param { number } courseId
     * @param { number } plagiarismComparisonId
     * @param { string } statement
     */
    public saveStudentStatement(courseId: number, plagiarismComparisonId: number, statement: string): Observable<StatementEntityResponseType> {
        return this.http.put<string>(`${this.resourceUrl}/${courseId}/plagiarism-comparisons/${plagiarismComparisonId}/student-statement`, { statement }, { observe: 'response' });
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
     * Update the status for student with the given studentLogin of the plagiarism comparison with given id
     * @param { number } courseId
     * @param { number } plagiarismComparisonId
     * @param { boolean } confirm
     * @param { string } studentLogin
     */
    public updatePlagiarismComparisonFinalStatus(courseId: number, plagiarismComparisonId: number, confirm: boolean, studentLogin: string): Observable<HttpResponse<void>> {
        return this.http.put<void>(
            `${this.resourceUrl}/${courseId}/plagiarism-comparisons/${plagiarismComparisonId}/final-status/${studentLogin}`,
            {
                status: confirm ? PlagiarismStatus.CONFIRMED : PlagiarismStatus.DENIED,
            },
            { observe: 'response' },
        );
    }
}
