import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisClientMessage, IrisMessage } from 'app/entities/iris/iris.model';

type EntityResponseType = HttpResponse<IrisMessage>;
type EntityArrayResponseType = HttpResponse<IrisMessage[]>;

/**
 * Provides a singleton instance of http client to perform CRUD operations.
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpMessageService {
    public resourceUrl = 'api/iris/sessions';

    constructor(private httpClient: HttpClient) {}

    /**
     * creates a message for a session
     * @param {number} sessionId
     * @param {IrisClientMessage} message
     * @return {Observable<EntityResponseType>}
     */
    createMessage(sessionId: number, message: IrisMessage): Observable<EntityResponseType> {
        return this.httpClient.post<IrisClientMessage>(`${this.resourceUrl}/${sessionId}/messages`, message, { observe: 'response' });
    }

    /**
     * gets all messages for a session by its id
     * @param {number} sessionId
     * @return {Observable<EntityArrayResponseType>}
     */
    getMessages(sessionId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<IrisMessage[]>(`${this.resourceUrl}${sessionId}/messages`, { observe: 'response' });
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
