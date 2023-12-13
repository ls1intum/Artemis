import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { IrisHttpSessionService } from 'app/iris/http-session.service';

type EntityResponseType = HttpResponse<IrisSession>;

/**
 * The `IrisHttpSessionService` provides methods for retrieving existing or creating new Iris sessions.
 * It interacts with the server-side API to perform session-related operations.
 */
@Injectable({ providedIn: 'root' })
export abstract class IrisExerciseHttpSessionService extends IrisHttpSessionService {
    protected constructor(
        http: HttpClient,
        protected sessionType: string,
    ) {
        super(http);
    }

    /**
     * gets the current session by the exerciseId
     * @param {number} exerciseId of the exercise
     * @return {Observable<EntityResponseType>} an Observable of the HTTP response
     */
    getCurrentSession(exerciseId: number): Observable<EntityResponseType> {
        return this.http.get<IrisSession>(`${this.apiPrefix}/programming-exercises/${exerciseId}/${this.sessionType}/current`, { observe: 'response' });
    }

    /**
     * creates a session for a programming exercise
     * @param {number} exerciseId of the session in which the message should be created
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    createSession(exerciseId: number): Observable<IrisSession> {
        return this.http.post<never>(`${this.apiPrefix}/programming-exercises/${exerciseId}/${this.sessionType}`, {});
    }
}
