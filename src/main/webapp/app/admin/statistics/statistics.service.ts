import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import { SpanType } from 'app/entities/statistics.model';

@Injectable({ providedIn: 'root' })
export class StatisticsService {
    private resourceUrl = SERVER_API_URL + 'api/management/statistics/';

    constructor(private http: HttpClient) {}

    /**
     * Sends a GET request to retrieve the amount of logged in users in the last *span* days
     */
    getloggedUsers(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}users`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of users with an submission in the last *span* days
     */
    getActiveUsers(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}activeUsers`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of submissions made in the last *span* days
     */
    getTotalSubmissions(span: SpanType): Observable<number[]> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number[]>(`${this.resourceUrl}submissions`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of released exercises in the last *span* days
     */
    getReleasedExercises(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}releasedExercises`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of exercises with due date in the last *span* days
     */
    getExerciseDeadlines(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}exerciseDeadlines`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of conducted exams in the last *span* days
     */
    getConductedExams(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}conductedExams`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of exam participations in the last *span* days
     */
    getExamParticipations(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}examParticipations`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of exam registrations in the last *span* days
     */
    getExamRegistrations(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}examRegistrations`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of tutors who created an assessment in the last *span* days
     */
    getActiveTutors(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}activeTutors`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of created results in the last *span* days
     */
    getCreatedResults(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}createdResults`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of feedback created for the results in the last *span* days
     */
    getResultFeedbacks(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}resultFeedbacks`, { params });
    }
}
