import { Injectable, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { IrisAssistantMessage, IrisMessage, IrisSender, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { BehaviorSubject, Observable, Subscription, catchError, map, of, tap, throwError } from 'rxjs';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { IrisTutorChatSession } from 'app/entities/iris/iris-tutor-chat-session.model';
import { IrisStageDTO } from 'app/entities/iris/iris-stage-dto.model';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { IrisChatWebsocketDTO, IrisChatWebsocketPayloadType } from 'app/entities/iris/iris-chat-websocket-dto.model';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import { IrisRateLimitInformation } from 'app/entities/iris/iris-ratelimit-info.model';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { UserService } from 'app/core/user/user.service';

/**
 * The IrisSessionService is responsible for managing Iris sessions and retrieving their associated messages.
 */
@Injectable()
export abstract class IrisChatService implements OnDestroy {
    sessionId?: number;
    messages: BehaviorSubject<IrisMessage[]> = new BehaviorSubject([]);
    numNewMessages: BehaviorSubject<number> = new BehaviorSubject(0);
    stages: BehaviorSubject<IrisStageDTO[]> = new BehaviorSubject([]);
    error: BehaviorSubject<IrisErrorMessageKey | undefined> = new BehaviorSubject(undefined);

    rateLimitInfo?: IrisRateLimitInformation;
    rateLimitSubscription: Subscription;

    /**
     * Creates an instance of IrisSessionService.
     * @param http The IrisChatHttpService for HTTP operations related to sessions.
     * @param ws The IrisChatWebsocketService for websocket operations
     * @param status The IrisStatusService for handling the status of the service.
     */
    protected constructor(
        public http: IrisChatHttpService,
        public ws: IrisWebsocketService,
        public status: IrisStatusService,
        private userService: UserService,
    ) {
        this.rateLimitSubscription = this.status.currentRatelimitInfo().subscribe((info) => (this.rateLimitInfo = info));
    }

    ngOnDestroy(): void {
        this.rateLimitSubscription.unsubscribe();
    }

    protected start() {
        this.getCurrentSessionOrCreate().subscribe(this.handleNewSession());
    }

    /**
     * Sends a message to the server and returns the created message.
     * @param message to be created
     */
    public sendMessage(message: string): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }

        const newMessage = new IrisUserMessage();
        newMessage.content = [new IrisTextMessageContent(message)];
        return this.http.createMessage(this.sessionId, newMessage).pipe(
            tap((m) => {
                this.messages.next([...this.messages.getValue(), m.body!]);
            }),
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                this.handleSendHttpError(error);
                return of();
            }),
        );
    }

    /**
     * Resends a message to the server and returns the created message.
     * @param message to be created
     */
    resendMessage(message: IrisUserMessage): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }

        return this.http.resendMessage(this.sessionId, message).pipe(
            map((r: HttpResponse<IrisUserMessage>) => r.body!),
            tap((m) => this.replaceMessage(m)),
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                this.handleSendHttpError(error);
                return of();
            }),
        );
    }

    private handleSendHttpError(error: HttpErrorResponse): void {
        if (error.status === 403) {
            this.error.next(IrisErrorMessageKey.IRIS_DISABLED);
        } else if (error.status === 429) {
            const map = new Map<string, any>();
            map.set('hours', this.rateLimitInfo?.rateLimitTimeframeHours);
            this.error.next(IrisErrorMessageKey.RATE_LIMIT_EXCEEDED);
        } else {
            this.error.next(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
        }
    }

    rateMessage(message: IrisAssistantMessage, helpful?: boolean): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }

        return this.http.rateMessage(this.sessionId, message.id!, !!helpful).pipe(
            map((r: HttpResponse<IrisAssistantMessage>) => r.body!),
            tap((m) => this.replaceMessage(m)),
            map(() => undefined),
            catchError(() => {
                this.error.next(IrisErrorMessageKey.RATE_MESSAGE_FAILED);
                return of();
            }),
        );
    }

    public messagesRead(): void {
        this.numNewMessages.next(0);
    }

    public setUserAccepted(): void {
        this.userService.acceptIris().subscribe(() => {
            this.initAfterAccept();
        });
    }

    protected abstract initAfterAccept(): void;

    private replaceMessage(message: IrisMessage) {
        const messages = [...this.messages.getValue()];
        const index = messages.findIndex((m) => m.id === message.id);
        if (index >= 0) {
            messages[index] = message;
            this.messages.next(messages);
        }
    }

    private handleNewSession() {
        return {
            next: (r: IrisSession) => {
                this.sessionId = r.id;
                this.messages.next(r.messages || []);
                this.ws.subscribeToSession(this.sessionId).subscribe((m) => this.handleWebsocketMessage(m));
            },
            error: (e: IrisErrorMessageKey) => {
                this.error.next(e as IrisErrorMessageKey);
            },
        };
    }

    public clearChat(): void {
        this.close();
        this.createNewSession().subscribe(this.handleNewSession());
    }

    private handleWebsocketMessage(payload: IrisChatWebsocketDTO) {
        if (payload.rateLimitInfo) {
            this.status.handleRateLimitInfo(payload.rateLimitInfo);
        }
        switch (payload.type) {
            case IrisChatWebsocketPayloadType.MESSAGE:
                if (payload.message?.sender === IrisSender.LLM) {
                    this.numNewMessages.next(this.numNewMessages.getValue() + 1);
                    this.messages.next([...this.messages.getValue(), payload.message!]);
                }
                if (payload.stages) {
                    this.stages.next(payload.stages);
                }
                break;
            case IrisChatWebsocketPayloadType.STATUS:
                this.stages.next(payload.stages || []);
                break;
        }
    }

    protected close(): void {
        if (this.sessionId) {
            this.ws.unsubscribeFromSession(this.sessionId);
            this.sessionId = undefined;
            this.messages.next([]);
            this.stages.next([]);
            this.numNewMessages.next(0);
        }
        this.error.next(undefined);
    }

    /**
     * Retrieves the current session or creates a new one if it doesn't exist.
     */
    private getCurrentSessionOrCreate(): Observable<IrisTutorChatSession> {
        return this.http.getCurrentSessionOrCreateIfNotExists(this.getSessionCreationIdentifier()).pipe(
            map((response: HttpResponse<IrisTutorChatSession>) => {
                if (response.body) {
                    return response.body;
                } else {
                    throw new Error(IrisErrorMessageKey.SESSION_LOAD_FAILED);
                }
            }),
            catchError(() => throwError(() => new Error(IrisErrorMessageKey.SESSION_LOAD_FAILED))),
        );
    }

    /**
     * Creates a new session
     */
    private createNewSession(): Observable<IrisTutorChatSession> {
        return this.http.createSession(this.getSessionCreationIdentifier()).pipe(
            map((response: HttpResponse<IrisTutorChatSession>) => {
                if (response.body) {
                    return response.body;
                } else {
                    throw new Error(IrisErrorMessageKey.SESSION_CREATION_FAILED);
                }
            }),
            catchError(() => throwError(() => new Error(IrisErrorMessageKey.SESSION_CREATION_FAILED))),
        );
    }

    protected abstract getSessionCreationIdentifier(): string;

    public currentMessages(): Observable<IrisMessage[]> {
        return this.messages.asObservable();
    }

    public currentStages(): Observable<IrisStageDTO[]> {
        return this.stages.asObservable();
    }

    public currentError(): Observable<IrisErrorMessageKey | undefined> {
        return this.error.asObservable();
    }

    public currentNumNewMessages(): Observable<number> {
        return this.numNewMessages.asObservable();
    }
}
