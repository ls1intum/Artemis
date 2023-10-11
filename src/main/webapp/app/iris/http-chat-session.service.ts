import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IrisHttpSessionService } from 'app/iris/http-session.service';

/**
 * The `IrisHttpChatSessionService` provides methods for retrieving existing or creating new Iris chat sessions.
 * It interacts with the server-side API to perform session-related operations.
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpChatSessionService extends IrisHttpSessionService {
    protected constructor(protected http: HttpClient) {
        super(http, 'sessions');
    }
}
