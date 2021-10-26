import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlagiarismCase } from 'app/course/plagiarism-cases/types/PlagiarismCase';
import { Notification } from 'app/entities/notification.model';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';

@Injectable({ providedIn: 'root' })
export class PlagiarismCasesService {
    private resourceUrl = SERVER_API_URL + '/api/plagiarism-cases';
    private resourceUrlStud = SERVER_API_URL + '/api/plagiarism-comparisons';

    constructor(private http: HttpClient) {}

    /**
     * Sends a GET request to retrieve the data for a graph based on the graphType in the last *span* days and the given period
     */
    public getPlagiarismCases(courseId: number): Observable<PlagiarismCase[]> {
        return this.http.get<PlagiarismCase[]>(`${this.resourceUrl}/${courseId}`);
    }

    public sendPlagiarismNotification(studentLogin: string, plagiarismComparisonId: number, instructorMessage: string): Observable<Notification> {
        return this.http.put(`${this.resourceUrl}/notification`, {
            studentLogin,
            plagiarismComparisonId,
            instructorMessage,
        });
    }

    /**
     * Sends a GET request to retrieve the data for a graph based on the graphType in the last *span* days and the given period
     */
    public getAnonymousPlagiarismComparison(plagiarismComparisonId: number): Observable<PlagiarismCase> {
        return this.http.get<PlagiarismCase>(`${this.resourceUrlStud}/${plagiarismComparisonId}`);
    }

    public sendStatement(plagiarismComparisonId: number, statement: string): Observable<string> {
        return this.http.put<string>(`${this.resourceUrl}/${plagiarismComparisonId}/statement`, { statement });
    }

    public updatePlagiarismStatus(confirm: boolean, comparisonId: number, studentLogin: string) {
        return this.http.put(`${this.resourceUrlStud}/${comparisonId}/status?finalDecision=true&studentLogin=${studentLogin}`, {
            status: confirm ? PlagiarismStatus.CONFIRMED : PlagiarismStatus.DENIED,
        });
    }
}
