import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisHttpSessionService } from 'app/iris/http-session.service';
import { IrisRateLimitInformation } from 'app/iris/websocket.service';

export class HeartbeatDTO {
    active: boolean;
    rateLimitInfo: IrisRateLimitInformation;
}
/**
 * The `IrisHttpChatSessionService` provides methods for retrieving existing or creating new Iris chat sessions.
 * It interacts with the server-side API to perform session-related operations.
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpChatSessionService extends IrisHttpSessionService {
    protected constructor(http: HttpClient) {
        super(http, 'sessions');
    }

    /**
     * Retrieves the heartbeat status of a session.
     * @param sessionId The ID of the session to check.
     * @return An Observable of the HTTP response containing a boolean value indicating the session's heartbeat status.
     */
    getHeartbeat(sessionId: number): Observable<HttpResponse<HeartbeatDTO>> {
        return this.http.get<HeartbeatDTO>(`${this.apiPrefix}/${this.sessionType}/${sessionId}/active`, { observe: 'response' });
    }
}
