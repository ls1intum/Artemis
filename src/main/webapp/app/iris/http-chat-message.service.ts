import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';

type EntityResponseType = HttpResponse<IrisMessage>;

/**
 * Provides a singleton root-level IrisHttpMessageService to perform CRUD operations on messages
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpChatMessageService extends IrisHttpMessageService {
    constructor(httpClient: HttpClient) {
        super(httpClient, 'api/iris/sessions');
    }

    /**
     * creates a rating for a message
     * @param {number} sessionId of the session of the message that should be rated
     * @param {number} messageId of the message that should be rated
     * @param {boolean} helpful rating of the message
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    rateMessage(sessionId: number, messageId: number, helpful: boolean): Observable<EntityResponseType> {
        return this.httpClient.put<IrisMessage>(`${this.resourceUrl}/${sessionId}/messages/${messageId}/helpful/${helpful}`, null, { observe: 'response' });
    }
}
