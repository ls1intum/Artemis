import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ConversationErrorOccurredAction, SessionReceivedAction } from 'app/iris/state-store.model';
import { IrisHttpSessionService } from 'app/iris/http-session.service';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { IrisMessage, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { firstValueFrom } from 'rxjs';

/**
 * The IrisSessionService is responsible for managing Iris sessions and retrieving their associated messages.
 */
@Injectable()
export abstract class IrisSessionService {
    /**
     * Creates an instance of IrisSessionService.
     * @param stateStore The IrisStateStore for managing the state of the application.
     * @param httpSessionService The IrisHttpSessionService for HTTP operations related to sessions.
     * @param httpMessageService The IrisHttpMessageService for HTTP operations related to messages.
     */
    protected constructor(
        protected readonly stateStore: IrisStateStore,
        public httpSessionService: IrisHttpSessionService,
        public httpMessageService: IrisHttpMessageService,
    ) {}

    /**
     * Retrieves the current session or creates a new one if it doesn't exist.
     * @param exerciseId The exercise ID to which the session will be attached.
     */
    getCurrentSessionOrCreate(exerciseId: number): void {
        let sessionId: number;

        this.httpSessionService
            .getCurrentSession(exerciseId)
            .toPromise()
            .then((irisSessionResponse: HttpResponse<IrisSession>) => {
                sessionId = irisSessionResponse.body!.id;
                return this.httpMessageService
                    .getMessages(sessionId)
                    .toPromise()
                    .then((messagesResponse: HttpResponse<IrisMessage[]>) => {
                        const messages = messagesResponse.body!;
                        messages.sort((a, b) => {
                            if (a.sentAt && b.sentAt) {
                                if (a.sentAt === b.sentAt) return 0;
                                return a.sentAt.isBefore(b.sentAt) ? -1 : 1;
                            }
                            return 0;
                        });
                        this.dispatchSuccess(sessionId, messages);
                    })
                    .catch(() => {
                        this.dispatchError(IrisErrorMessageKey.HISTORY_LOAD_FAILED);
                    });
            })
            .catch((error: HttpErrorResponse) => {
                if (error.status == 404) {
                    return this.createNewSession(exerciseId);
                } else {
                    this.dispatchError(IrisErrorMessageKey.SESSION_LOAD_FAILED);
                }
            });
    }

    /**
     * Creates a new session for the given exercise ID.
     * @param exerciseId The exercise ID for which to create a new session.
     */
    createNewSession(exerciseId: number): void {
        this.httpSessionService.createSession(exerciseId).subscribe(
            (irisSessionResponse: any) => {
                this.dispatchSuccess(irisSessionResponse.id, []);
            },
            () => this.dispatchError(IrisErrorMessageKey.SESSION_CREATION_FAILED),
        );
    }

    /**
     * Sends a message to the server and returns the created message.
     * @param sessionId of the session in which the message should be created
     * @param message to be created
     * @param args optional arguments to be sent with the message
     */
    async sendMessage(sessionId: number, message: IrisUserMessage, args?: Record<string, unknown>): Promise<IrisMessage> {
        const response = await firstValueFrom(this.httpMessageService.createMessage(sessionId, message, args));
        return response.body!;
    }

    /**
     * Resends a message to the server and returns the created message.
     * @param sessionId of the session in which the message should be created
     * @param message to be created
     * @param args optional arguments to be sent with the message
     */
    async resendMessage(sessionId: number, message: IrisUserMessage, args?: Record<string, unknown>): Promise<IrisMessage> {
        const response = await firstValueFrom(this.httpMessageService.resendMessage(sessionId, message, args));
        return response.body!;
    }

    async rateMessage(sessionId: number, messageId: number, helpful: boolean): Promise<IrisMessage> {
        const response = await firstValueFrom(this.httpMessageService.rateMessage(sessionId, messageId, helpful));
        return response.body!;
    }

    private dispatchSuccess(sessionId: number, messages: IrisMessage[]): void {
        this.stateStore.dispatch(new SessionReceivedAction(sessionId, messages));
    }

    /**
     * Dispatches an error action with the specified error message.
     * @param error The error message.
     */
    private dispatchError(error: IrisErrorMessageKey): void {
        this.stateStore.dispatch(new ConversationErrorOccurredAction(error));
    }
}
