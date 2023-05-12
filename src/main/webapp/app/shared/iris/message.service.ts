import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisMessage } from 'app/entities/iris/message.model';

type EntityResponseType = HttpResponse<IrisMessage>;
type EntityArrayResponseType = HttpResponse<IrisMessage[]>;

@Injectable({ providedIn: 'root' })
export class MessageService {
    public resourceUrl = SERVER_API_URL + 'api/iris/sessions';

    constructor(protected http: HttpClient) {
        this.http = http;
    }

    /**
     * gets all messages of a session by its id
     * @param {number} sessionId of the session
     * @return {Observable<EntityArrayResponseType>} an Observable of the HTTP response array
     */
    getMessages(sessionId: number): Observable<EntityArrayResponseType> {
        return this.http.get<IrisMessage[]>(`${this.resourceUrl}${sessionId}/messages`, { observe: 'response' });
    }

    /**
     * creates a message for a session
     * @param {number} sessionId of the session in which the message should be created
     * @param {IrisMessage} message the message to be created
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    createMessage(sessionId: number, message: IrisMessage): Observable<EntityResponseType> {
        return this.http.post<IrisMessage>(`${this.resourceUrl}/${sessionId}/messages`, message, { observe: 'response' });
    }

    /**
     * creates a rating for a message
     * @param {number} sessionId of the session of the message that should be rated
     * @param {number} messageId of the message that should be rated
     * @param {boolean} helpful rating of the message
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */

    rateMessage(sessionId: number, messageId: number, helpful: boolean): Observable<EntityResponseType> {
        return this.http.put<IrisMessage>(`${this.resourceUrl}/${sessionId}/messages/${messageId}/helpful/${helpful}`, null, { observe: 'response' });
    }
}
