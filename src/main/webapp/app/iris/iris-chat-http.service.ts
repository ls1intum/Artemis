import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisAssistantMessage, IrisMessage, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { map, tap } from 'rxjs/operators';
import { IrisSession } from 'app/entities/iris/iris-session.model';

export type Response<T> = Observable<HttpResponse<T>>;

/**
 * Provides a set of methods to perform CRUD operations on messages
 */
@Injectable({ providedIn: 'root' })
export class IrisChatHttpService {
    protected httpClient = inject(HttpClient);

    protected apiPrefix: string = 'api/iris';

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
        return this.httpClient.get<IrisMessage[]>(`${this.apiPrefix}/sessions/${sessionId}/messages`, { observe: 'response' }).pipe(
            map((response) => {
                const messages = response.body;
                if (!messages) return response;

                const modifiedMessages = messages.map((message) => {
                    return Object.assign({}, message, {
                        sentAt: convertDateFromServer(message.sentAt),
                    });
                });

                modifiedMessages.sort((a, b) => {
                    if (a.sentAt && b.sentAt) {
                        if (a.sentAt === b.sentAt) return 0;
                        return a.sentAt.isBefore(b.sentAt) ? -1 : 1;
                    }
                    return 0;
                });

                return Object.assign({}, response, {
                    body: modifiedMessages,
                });
            }),
        );
    }

    /**
     * creates a new message in a session
     * @param sessionId of the session
     * @param message  to be created
     */
    createMessage(sessionId: number, message: IrisUserMessage): Response<IrisUserMessage> {
        message.messageDifferentiator = this.randomInt();
        return this.httpClient.post<IrisUserMessage>(
            `${this.apiPrefix}/sessions/${sessionId}/messages`,
            Object.assign({}, message, {
                sentAt: convertDateFromClient(message.sentAt),
            }),
            { observe: 'response' },
        );
    }

    /**
     * resends a message in a session
     * @param {number} sessionId
     * @param {IrisUserMessage} message
     * @return {Response<IrisMessage>}
     */
    resendMessage(sessionId: number, message: IrisUserMessage): Response<IrisMessage> {
        message.messageDifferentiator = message.messageDifferentiator ?? this.randomInt();
        return this.httpClient.post<IrisAssistantMessage>(`${this.apiPrefix}/sessions/${sessionId}/messages/${message.id}/resend`, null, { observe: 'response' }).pipe(
            tap((response) => {
                if (response.body && response.body.id) {
                    message.id = response.body.id;
                }
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
        return this.httpClient.put<IrisMessage>(`${this.apiPrefix}/sessions/${sessionId}/messages/${messageId}/helpful/${helpful}`, null, { observe: 'response' });
    }

    getCurrentSessionOrCreateIfNotExists<T extends IrisSession>(identifier: string): Response<T> {
        return this.httpClient.post<T>(`${this.apiPrefix}/${identifier}/sessions/current`, null, { observe: 'response' });
    }

    createSession<T extends IrisSession>(identifier: string): Response<T> {
        return this.httpClient.post<T>(`${this.apiPrefix}/${identifier}/sessions`, null, { observe: 'response' });
    }
}
