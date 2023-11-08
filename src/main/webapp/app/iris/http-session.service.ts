import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { IrisRateLimitInformation } from 'app/iris/websocket.service';

type EntityResponseType = HttpResponse<IrisSession>;

export class HeartbeatDTO {
    active: boolean;
    rateLimitInfo: IrisRateLimitInformation;
}

/**
 * The `IrisHttpSessionService` provides methods for retrieving existing or creating new Iris sessions.
 * It interacts with the server-side API to perform session-related operations.
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

    /**
     * Retrieves the heartbeat status of a session.
     * @param sessionId The ID of the session to check.
     * @return An Observable of the HTTP response containing a boolean value indicating the session's heartbeat status.
     */
    getHeartbeat(sessionId: number): Observable<HttpResponse<HeartbeatDTO>> {
        return this.http.get<HeartbeatDTO>(`${this.resourceUrl}/sessions/${sessionId}/active`, { observe: 'response' });
    }
}
