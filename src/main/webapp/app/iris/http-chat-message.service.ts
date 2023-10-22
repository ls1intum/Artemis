import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IrisHttpMessageService, Response } from 'app/iris/http-message.service';
import { IrisMessage, IrisServerMessage, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { convertDateFromClient } from 'app/utils/date.utils';
import { tap } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class IrisHttpChatMessageService extends IrisHttpMessageService {
    constructor(httpClient: HttpClient) {
        super(httpClient, 'sessions');
    }

    /**
     * creates a message for a chat session
     * @param {number} sessionId
     * @param {IrisUserMessage} message
     * @return {Response<IrisMessage>}
     */
    createMessage(sessionId: number, message: IrisUserMessage): Response<IrisMessage> {
        message.messageDifferentiator = this.randomInt();
        return this.httpClient
            .post<IrisServerMessage>(
                `${this.apiPrefix}/${this.sessionType}/${sessionId}/messages`,
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
     * resends a message for a chat session
     * @param {number} sessionId
     * @param {IrisUserMessage} message
     * @return {Response<IrisMessage>}
     */
    resendMessage(sessionId: number, message: IrisUserMessage): Response<IrisMessage> {
        message.messageDifferentiator = message.messageDifferentiator ?? this.randomInt();
        return this.httpClient.post<IrisServerMessage>(`${this.apiPrefix}/${this.sessionType}/${sessionId}/messages/${message.id}/resend`, null, { observe: 'response' }).pipe(
            tap((response) => {
                if (response.body && response.body.id) {
                    message.id = response.body.id;
                }
            }),
        );
    }
}
