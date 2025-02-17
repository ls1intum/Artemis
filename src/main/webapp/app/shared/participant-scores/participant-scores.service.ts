import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export class ScoresDTO {
    public studentId?: number;
    public studentLogin?: string;
    public pointsAchieved?: number;
    public scoreAchieved?: number;
    public regularPointsAchievable?: number;
}

@Injectable({ providedIn: 'root' })
export class ParticipantScoresService {
    private http = inject(HttpClient);

    public resourceUrl = 'api';

    findCourseScores(courseId: number): Observable<HttpResponse<ScoresDTO[]>> {
        return this.http.get<ScoresDTO[]>(`${this.resourceUrl}/courses/${courseId}/course-scores`, {
            observe: 'response',
        });
    }

    findExamScores(examId: number): Observable<HttpResponse<ScoresDTO[]>> {
        return this.http.get<ScoresDTO[]>(`${this.resourceUrl}/exams/${examId}/exam-scores`, {
            observe: 'response',
        });
    }
}
