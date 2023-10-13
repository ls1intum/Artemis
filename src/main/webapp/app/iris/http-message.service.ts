import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisClientMessage, IrisMessage, IrisServerMessage } from 'app/entities/iris/iris-message.model';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { map, tap } from 'rxjs/operators';

type EntityResponseType = HttpResponse<IrisMessage>;
type EntityArrayResponseType = HttpResponse<IrisMessage[]>;

/**
 * Provides a singleton root-level IrisHttpMessageService to perform CRUD operations on messages
 */
@Injectable({ providedIn: 'root' })
export abstract class IrisHttpMessageService {
    protected resourceUrl: string;

    protected constructor(
        protected httpClient: HttpClient,
        resourceUrl: string,
    ) {
        this.resourceUrl = resourceUrl;
    }

    protected readonly MAX_INT_JAVA = 2147483647;

    /**
     * creates a message for a session
     * @param {number} sessionId
     * @param {IrisClientMessage} message
     * @return {Observable<EntityResponseType>}
     */
    createMessage(sessionId: number, message: IrisClientMessage): Observable<EntityResponseType> {
        message.messageDifferentiator = Math.floor(Math.random() * this.MAX_INT_JAVA);
        return this.httpClient
            .post<IrisServerMessage>(
                `${this.resourceUrl}/${sessionId}/messages`,
                Object.assign({}, message, {
                    sentAt: convertDateFromClient(message.sentAt),
                }),
                { observe: 'response' },
            )
            .pipe(
                tap((response) => {
                    if (response.body && response.body.id) {
                        message.id = response.body.id;
                    }
                }),
            );
    }

    /**
     * resends a message for a session
     * @param {number} sessionId
     * @param {IrisClientMessage} message
     * @return {Observable<EntityResponseType>}
     */
    resendMessage(sessionId: number, message: IrisClientMessage): Observable<EntityResponseType> {
        message.messageDifferentiator = message.messageDifferentiator ?? Math.floor(Math.random() * this.MAX_INT_JAVA);
        return this.httpClient.post<IrisServerMessage>(`${this.resourceUrl}/${sessionId}/messages/${message.id}/resend`, undefined, { observe: 'response' }).pipe(
            tap((response) => {
                if (response.body && response.body.id) {
                    message.id = response.body.id;
                }
            }),
        );
    }

    /**
     * gets all messages for a session by its id
     * @param {number} sessionId
     * @return {Observable<EntityArrayResponseType>}
     */
    getMessages(sessionId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<IrisMessage[]>(`${this.resourceUrl}/${sessionId}/messages`, { observe: 'response' }).pipe(
            map((response) => {
                const messages = response.body;
                if (messages == null) return response;

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
}
