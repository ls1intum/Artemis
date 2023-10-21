import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { convertDateFromServer } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';

export type Response<T> = Observable<HttpResponse<T>>;

/**
 * Provides a set of methods to perform CRUD operations on messages
 */
@Injectable({ providedIn: 'root' })
export abstract class IrisHttpMessageService {
    protected apiPrefix: string = 'api/iris/';

    protected constructor(
        protected httpClient: HttpClient,
        protected sessionType: string,
    ) {}

    protected randomInt(): number {
        const maxIntJava = 2147483647;
        return Math.floor(Math.random() * maxIntJava);
    }

    /**
     * gets all messages for a session by its id
     * @param {number} sessionId
     * @return {Observable<EntityArrayResponseType>}
     */
    getMessages(sessionId: number): Response<IrisMessage[]> {
        return this.httpClient.get<IrisMessage[]>(`${this.apiPrefix}/${this.sessionType}/${sessionId}/messages`, { observe: 'response' }).pipe(
            map((response) => {
                const messages = response.body;
                if (!messages) return response;

                const modifiedMessages = messages.map((message) => {
                    return Object.assign({}, message, {
                        sentAt: convertDateFromServer(message.sentAt),
                    });
                });

                return Object.assign({}, response, {
                    body: modifiedMessages,
                });
            }),
        );
    }

    /**
     * Sets a helpfulness rating for a message
     * @param {number} sessionId of the session of the message that should be rated
     * @param {number} messageId of the message that should be rated
     * @param {boolean} helpful rating of the message
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    rateMessage(sessionId: number, messageId: number, helpful: boolean): Response<IrisMessage> {
        return this.httpClient.put<IrisMessage>(`${this.apiPrefix}/${this.sessionType}/${sessionId}/messages/${messageId}/helpful/${helpful}`, null, { observe: 'response' });
    }
}
