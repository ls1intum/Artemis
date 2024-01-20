import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisMessage, IrisServerMessage, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { map, tap } from 'rxjs/operators';

export type Response<T> = Observable<HttpResponse<T>>;

/**
 * Provides a set of methods to perform CRUD operations on messages
 */
@Injectable({ providedIn: 'root' })
export abstract class IrisHttpMessageService {
    protected apiPrefix: string = 'api/iris';

    protected constructor(protected httpClient: HttpClient) {}

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
        // @formatter:off
        return this.httpClient.get<IrisMessage[]>(`${this.apiPrefix}/sessions/${sessionId}/messages`, { observe: 'response' }).pipe(
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
        // @formatter:on
    }

    /**
     * creates a new message in a session
     * @param sessionId of the session
     * @param message  to be created
     * @param context optional arguments
     */
    createMessage(sessionId: number, message: IrisUserMessage, context?: Record<string, unknown>): Response<IrisMessage> {
        message.messageDifferentiator = this.randomInt();
        const payload = {
            message: Object.assign({}, message, {
                sentAt: convertDateFromClient(message.sentAt),
            }),
            context: context ?? {},
        };
        // @formatter:off
        return this.httpClient.post<IrisServerMessage>(`${this.apiPrefix}/sessions/${sessionId}/messages`, payload, { observe: 'response' }).pipe(
            tap((response) => {
                if (response.body && response.body.id) {
                    message.id = response.body.id;
                }
            }),
        );
        // @formatter:on
    }

    /**
     * resends a message in a session
     * @param {number} sessionId of the session
     * @param {IrisUserMessage} message to be resent
     * @param context optional arguments
     * @return {Response<IrisMessage>} an Observable of the HTTP responses
     */
    resendMessage(sessionId: number, message: IrisUserMessage, context?: Record<string, unknown>): Response<IrisMessage> {
        message.messageDifferentiator = message.messageDifferentiator ?? this.randomInt();
        // @formatter:off
        return this.httpClient.post<IrisServerMessage>(`${this.apiPrefix}/sessions/${sessionId}/messages/${message.id}/resend`, context ?? {}, { observe: 'response' }).pipe(
            tap((response) => {
                if (response.body && response.body.id) {
                    message.id = response.body.id;
                }
            }),
        );
        // @formatter:on
    }

    /**
     * Sets a helpfulness rating for a message
     * @param {number} sessionId of the session of the message that should be rated
     * @param {number} messageId of the message that should be rated
     * @param {boolean} helpful rating of the message
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    rateMessage(sessionId: number, messageId: number, helpful: boolean): Response<IrisMessage> {
        // @formatter:off
        return this.httpClient.put<IrisMessage>(`${this.apiPrefix}/sessions/${sessionId}/messages/${messageId}/helpful/${helpful}`, null, { observe: 'response' });
        // @formatter:on
    }
}
