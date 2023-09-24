import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SuspiciousExamSessions, SuspiciousSessionsAnalysisOptions } from 'app/entities/exam-session.model';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class SuspiciousSessionsService {
    constructor(private http: HttpClient) {}

    getSuspiciousSessions(courseId: number, examId: number, options: SuspiciousSessionsAnalysisOptions): Observable<SuspiciousExamSessions[]> {
        let params = new HttpParams()
            .set('differentStudentExamsSameIPAddress', options.differentStudentExamsSameIPAddress.toString())
            .set('differentStudentExamsSameBrowserFingerprint', options.differentStudentExamsSameBrowserFingerprint.toString())
            .set('sameStudentExamDifferentIPAddresses', options.sameStudentExamDifferentIPAddresses.toString())
            .set('sameStudentExamDifferentBrowserFingerprints', options.sameStudentExamDifferentBrowserFingerprints.toString())
            .set('ipOutsideOfRange', options.ipOutsideOfRange.toString());

        // If subnet is provided, add it to the params
        if (options.ipSubnet) {
            params = params.set('ipSubnet', options.ipSubnet);
        }
        return this.http.get<SuspiciousExamSessions[]>(`api/courses/${courseId}/exams/${examId}/suspicious-sessions`, { params });
    }
}
