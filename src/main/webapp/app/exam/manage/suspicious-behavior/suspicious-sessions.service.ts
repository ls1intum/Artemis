import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { SuspiciousExamSessions, SuspiciousSessionsAnalysisOptions } from 'app/entities/exam/exam-session.model';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class SuspiciousSessionsService {
    private http = inject(HttpClient);

    getSuspiciousSessions(courseId: number, examId: number, options: SuspiciousSessionsAnalysisOptions): Observable<SuspiciousExamSessions[]> {
        let params = new HttpParams()
            .set('differentStudentExamsSameIPAddress', options.sameIpAddressDifferentStudentExams.toString())
            .set('differentStudentExamsSameBrowserFingerprint', options.sameBrowserFingerprintDifferentStudentExams.toString())
            .set('sameStudentExamDifferentIPAddresses', options.differentIpAddressesSameStudentExam.toString())
            .set('sameStudentExamDifferentBrowserFingerprints', options.differentIpAddressesSameStudentExam.toString())
            .set('ipOutsideOfRange', options.ipAddressOutsideOfRange.toString());

        // If subnet is provided, add it to the params
        if (options.ipSubnet) {
            params = params.set('ipSubnet', options.ipSubnet);
        }
        return this.http.get<SuspiciousExamSessions[]>(`api/courses/${courseId}/exams/${examId}/suspicious-sessions`, { params });
    }
}
