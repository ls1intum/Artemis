import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';

export type EntityResponseType = HttpResponse<PlagiarismCase>;
export type EntityArrayResponseType = HttpResponse<PlagiarismCase[]>;
export type StatementEntityResponseType = HttpResponse<string>;

@Injectable({ providedIn: 'root' })
export class PlagiarismCasesService {
    private resourceUrl = SERVER_API_URL + 'api/courses';
    private resourceUrlStudent = SERVER_API_URL + 'api/plagiarism-comparisons';

    constructor(private http: HttpClient) {}

    /**
     * Get all plagiarism cases for the course with the given id
     * @param { number } courseId
     */
    public getPlagiarismCases(courseId: number): Observable<EntityArrayResponseType> {
        return this.http.get<PlagiarismCase[]>(`${this.resourceUrl}/${courseId}/plagiarism-cases`, { observe: 'response' });
    }

    /**
     * Get the plagiarism comparison with the given id
     * Anonymizes the submission of the other student
     * @param { number } plagiarismComparisonId
     */
    public getPlagiarismComparisonForStudent(plagiarismComparisonId: number): Observable<EntityResponseType> {
        return this.http.get<PlagiarismCase>(`${this.resourceUrlStudent}/${plagiarismComparisonId}`, { observe: 'response' });
    }

    /**
     * Update the instructorStatement for student with the given studentLogin of the plagiarism comparison with given id
     * @param { number } plagiarismComparisonId
     * @param { string } studentLogin
     * @param { string } statement
     */
    public saveInstructorStatement(plagiarismComparisonId: number, studentLogin: string, statement: string): Observable<StatementEntityResponseType> {
        return this.http.put<string>(`${this.resourceUrl}/notification`, { statement }, { observe: 'response' });
    }

    /**
     * Update the studentStatement of the plagiarism comparison with given id
     * @param { number } plagiarismComparisonId
     * @param { string } statement
     */
    public saveStudentStatement(plagiarismComparisonId: number, statement: string): Observable<StatementEntityResponseType> {
        return this.http.put<string>(`${this.resourceUrl}/${plagiarismComparisonId}/statement`, { statement }, { observe: 'response' });
    }

    /**
     * Update the status of the plagiarism comparison with given id
     * @param { number } plagiarismComparisonId
     * @param { PlagiarismStatus } status
     */
    public updatePlagiarismComparisonStatus(plagiarismComparisonId: number, status: PlagiarismStatus): Observable<HttpResponse<void>> {
        return this.http.put<void>(`${this.resourceUrlStudent}/${plagiarismComparisonId}/status`, { status }, { observe: 'response' });
    }

    /**
     * Update the status for student with the given studentLogin of the plagiarism comparison with given id
     * @param { number } plagiarismComparisonId
     * @param { boolean } confirm
     * @param { string } studentLogin
     */
    public updatePlagiarismComparisonFinalStatus(plagiarismComparisonId: number, confirm: boolean, studentLogin: string): Observable<HttpResponse<void>> {
        return this.http.put<void>(
            `${this.resourceUrlStudent}/${plagiarismComparisonId}/status?finalDecision=true&studentLogin=${studentLogin}`,
            {
                status: confirm ? PlagiarismStatus.CONFIRMED : PlagiarismStatus.DENIED,
            },
            { observe: 'response' },
        );
    }
}
