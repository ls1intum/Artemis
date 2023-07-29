import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SuspiciousExamSessions } from 'app/entities/exam-session.model';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class SuspiciousSessionsService {
    constructor(private http: HttpClient) {}

    getSuspiciousSessions(courseId: number, examId: number): Observable<SuspiciousExamSessions[]> {
        return this.http.get<SuspiciousExamSessions[]>(`api/courses/${courseId}/exams/${examId}/suspicious-sessions`);
    }
}
