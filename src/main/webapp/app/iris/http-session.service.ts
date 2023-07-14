import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisSession } from 'app/entities/iris/iris-session.model';

type EntityResponseType = HttpResponse<IrisSession>;

/**
 * Provides a singleton root-level IrisHttpSessionService to retrieve existing or create new sessions
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpSessionService {
    public resourceUrl = 'api/iris';

    constructor(protected http: HttpClient) {
        this.http = http;
    }

    /**
     * gets the current session by the exerciseId
     * @param {number} exerciseId of the exercise
     * @return {Observable<EntityResponseType>} an Observable of the HTTP response
     */
    getCurrentSession(exerciseId: number): Observable<EntityResponseType> {
        return this.http.get<IrisSession>(`${this.resourceUrl}/programming-exercises/${exerciseId}/sessions/current`, { observe: 'response' });
    }

    /**
     * creates a session for a programming exercise
     * @param {number} exerciseId of the session in which the message should be created
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    createSessionForProgrammingExercise(exerciseId: number): Observable<IrisSession> {
        return this.http.post<never>(`${this.resourceUrl}/programming-exercises/${exerciseId}/sessions`, {});
    }
}
