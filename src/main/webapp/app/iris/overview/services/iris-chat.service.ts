import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { IrisAssistantMessage, IrisMessage, IrisSender, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { BehaviorSubject, Observable, Subscription, catchError, map, of, tap, throwError } from 'rxjs';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisExerciseChatSession } from 'app/iris/shared/entities/iris-exercise-chat-session.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisChatWebsocketDTO, IrisChatWebsocketPayloadType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { UserService } from 'app/core/user/shared/user.service';
import { AccountService } from 'app/core/auth/account.service';

export enum ChatServiceMode {
    TEXT_EXERCISE = 'TEXT_EXERCISE_CHAT',
    PROGRAMMING_EXERCISE = 'PROGRAMMING_EXERCISE_CHAT',
    COURSE = 'COURSE_CHAT',
    LECTURE = 'LECTURE_CHAT',
    TUTOR_SUGGESTION = 'TUTOR_SUGGESTION',
}

export function chatModeToUrlComponent(mode: ChatServiceMode): string | undefined {
    switch (mode) {
        case ChatServiceMode.COURSE:
            return 'course-chat';
        case ChatServiceMode.LECTURE:
            return 'lecture-chat';
        case ChatServiceMode.PROGRAMMING_EXERCISE:
            return 'programming-exercise-chat';
        case ChatServiceMode.TEXT_EXERCISE:
            return 'text-exercise-chat';
        default:
            return undefined;
    }
}

/**
 * The IrisSessionService is responsible for managing Iris sessions and retrieving their associated messages.
 */
@Injectable({ providedIn: 'root' })
export class IrisChatService implements OnDestroy {
    private readonly http = inject(IrisChatHttpService);
    private readonly ws = inject(IrisWebsocketService);
    private readonly status = inject(IrisStatusService);
    private readonly userService = inject(UserService);
    private readonly accountService = inject(AccountService);

    private modeRequiresLLMAcceptance = new Map<ChatServiceMode, boolean>([
        [ChatServiceMode.TEXT_EXERCISE, true],
        [ChatServiceMode.PROGRAMMING_EXERCISE, true],
        [ChatServiceMode.COURSE, true],
        [ChatServiceMode.LECTURE, true],
        [ChatServiceMode.TUTOR_SUGGESTION, false],
    ]);

    private sessionIdSubject = new BehaviorSubject<number | undefined>(undefined);
    public sessionId$ = this.sessionIdSubject.asObservable();

    public get sessionId(): number | undefined {
        return this.sessionIdSubject.value;
    }

    public set sessionId(id: number | undefined) {
        this.sessionIdSubject.next(id);
    }
    messages: BehaviorSubject<IrisMessage[]> = new BehaviorSubject([]);
    newIrisMessage: BehaviorSubject<IrisMessage | undefined> = new BehaviorSubject(undefined);
    numNewMessages: BehaviorSubject<number> = new BehaviorSubject(0);
    stages: BehaviorSubject<IrisStageDTO[]> = new BehaviorSubject([]);
    suggestions: BehaviorSubject<string[]> = new BehaviorSubject([]);
    error: BehaviorSubject<IrisErrorMessageKey | undefined> = new BehaviorSubject(undefined);
    chatSessions: BehaviorSubject<IrisSession[]> = new BehaviorSubject([]);

    rateLimitInfo?: IrisRateLimitInformation;
    rateLimitSubscription: Subscription;
    private acceptSubscription?: Subscription;

    private sessionCreationIdentifier?: string;

    hasJustAcceptedExternalLLMUsage = false;

    private courseId = 0;

    protected constructor() {
        this.rateLimitSubscription = this.status.currentRatelimitInfo().subscribe((info) => (this.rateLimitInfo = info));
    }

    ngOnDestroy(): void {
        this.rateLimitSubscription.unsubscribe();
        this.acceptSubscription?.unsubscribe();
    }

    protected start() {
        const requiresAcceptance = this.sessionCreationIdentifier
            ? this.modeRequiresLLMAcceptance.get(Object.values(ChatServiceMode).find((mode) => this.sessionCreationIdentifier?.includes(mode)) as ChatServiceMode)
            : true;
        if (requiresAcceptance === false || this.accountService.userIdentity?.externalLLMUsageAccepted || this.hasJustAcceptedExternalLLMUsage) {
            this.getCurrentSessionOrCreate().subscribe({
                ...this.handleNewSession(),
                complete: () => this.loadChatSessions(),
            });
        }
    }

    /**
     * Sends a message to the server and returns the created message.
     * @param message to be created
     */
    public sendMessage(message: string): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }
        this.suggestions.next([]);

        // Trim messages (Spaces, newlines)
        message = message.trim();

        const newMessage = new IrisUserMessage();
        newMessage.content = [new IrisTextMessageContent(message)];
        return this.http.createMessage(this.sessionId, newMessage).pipe(
            tap((m) => {
                this.replaceOrAddMessage(m.body!);
            }),
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                this.handleSendHttpError(error);
                return of();
            }),
        );
    }

    /**
     * requests a tutor suggestion from the server
     */
    public requestTutorSuggestion(): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }
        return this.http.createTutorSuggestion(this.sessionId).pipe(
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                this.handleSendHttpError(error);
                return of();
            }),
        );
    }

    private replaceOrAddMessage(message: IrisMessage) {
        const messageWasReplaced = this.replaceMessage(message);
        if (!messageWasReplaced) {
            if (message.sender === IrisSender.LLM) {
                this.newIrisMessage.next(message);
            }
            this.messages.next([...this.messages.getValue(), message]);
        }
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
        this.newIrisMessage.next(undefined);
    }

    public updateExternalLLMUsageConsent(accepted: boolean): void {
        this.acceptSubscription?.unsubscribe();
        this.acceptSubscription = this.userService.updateExternalLLMUsageConsent(accepted).subscribe(() => {
            this.hasJustAcceptedExternalLLMUsage = accepted;
            this.accountService.setUserAcceptedExternalLLMUsage(accepted);
            this.closeAndStart();
        });
    }

    private replaceMessage(message: IrisMessage): boolean {
        const messages = [...this.messages.getValue()];
        const index = messages.findIndex((m) => m.id === message.id);
        if (index >= 0) {
            messages[index] = message;
            this.messages.next(messages);
            return true;
        }
        return false;
    }

    private handleNewSession() {
        return {
            next: (r: IrisSession) => {
                this.sessionId = r.id;
                this.messages.next(r.messages || []);
                this.parseLatestSuggestions(r.latestSuggestions);
                this.ws.subscribeToSession(this.sessionId).subscribe((m) => this.handleWebsocketMessage(m));
            },
            error: (e: IrisErrorMessageKey) => {
                this.error.next(e as IrisErrorMessageKey);
            },
        };
    }

    /**
     * Parses the latest suggestions string and updates the suggestions subject.
     * @param s: The latest suggestions string
     * @private
     */
    private parseLatestSuggestions(s?: string) {
        if (!s) {
            this.suggestions.next([]);
            return;
        }

        const suggestions = JSON.parse(s);
        this.suggestions.next(suggestions);
    }

    public clearChat(): void {
        this.close();
        this.createNewSession().subscribe({
            ...this.handleNewSession(),
            complete: () => this.loadChatSessions(),
        });
    }

    private handleWebsocketMessage(payload: IrisChatWebsocketDTO) {
        if (payload.rateLimitInfo) {
            this.status.handleRateLimitInfo(payload.rateLimitInfo);
        }
        switch (payload.type) {
            case IrisChatWebsocketPayloadType.MESSAGE:
                if (payload.message?.sender === IrisSender.LLM) {
                    this.numNewMessages.next(this.numNewMessages.getValue() + 1);
                }
                if (payload.message?.id) {
                    this.replaceOrAddMessage(payload.message);
                }
                if (payload.stages) {
                    this.stages.next(payload.stages);
                }
                break;
            case IrisChatWebsocketPayloadType.STATUS:
                this.stages.next(payload.stages || []);
                if (payload.suggestions) {
                    this.suggestions.next(payload.suggestions);
                }
                break;
        }
    }

    protected close(): void {
        if (this.sessionId) {
            this.ws.unsubscribeFromSession(this.sessionId);
            this.sessionId = undefined;
            this.messages.next([]);
            this.stages.next([]);
            this.suggestions.next([]);
            this.numNewMessages.next(0);
            this.newIrisMessage.next(undefined);
        }
        this.error.next(undefined);
    }

    /**
     * Retrieves the current session or creates a new one if it doesn't exist.
     */
    private getCurrentSessionOrCreate(): Observable<IrisExerciseChatSession> {
        if (!this.sessionCreationIdentifier) {
            throw new Error('Session creation identifier not set');
        }

        return this.http.getCurrentSessionOrCreateIfNotExists(this.sessionCreationIdentifier).pipe(
            map((response: HttpResponse<IrisExerciseChatSession>) => {
                if (response.body) {
                    return response.body;
                } else {
                    throw new Error(IrisErrorMessageKey.SESSION_LOAD_FAILED);
                }
            }),
            catchError(() => throwError(() => new Error(IrisErrorMessageKey.SESSION_LOAD_FAILED))),
        );
    }

    private loadChatSessions() {
        this.http.getChatSessions(this.courseId).subscribe((sessions: IrisSession[]) => {
            this.chatSessions.next(sessions ?? []);
        });
    }

    /**
     * Creates a new session
     */
    private createNewSession(): Observable<IrisExerciseChatSession> {
        if (!this.sessionCreationIdentifier) {
            throw new Error('Session creation identifier not set');
        }
        return this.http.createSession(this.sessionCreationIdentifier).pipe(
            map((response: HttpResponse<IrisExerciseChatSession>) => {
                if (response.body) {
                    return response.body;
                } else {
                    throw new Error(IrisErrorMessageKey.SESSION_CREATION_FAILED);
                }
            }),
            catchError(() => throwError(() => new Error(IrisErrorMessageKey.SESSION_CREATION_FAILED))),
        );
    }

    switchTo(mode: ChatServiceMode, id?: number): void {
        const modeUrl = chatModeToUrlComponent(mode);
        const newIdentifier = modeUrl && id ? modeUrl + '/' + id : undefined;
        const isDifferent = this.sessionCreationIdentifier !== newIdentifier;
        this.sessionCreationIdentifier = newIdentifier;
        if (isDifferent) {
            this.closeAndStart();
        }
    }

    switchToSession(session: IrisSession): void {
        if (this.sessionId === session.id) {
            return;
        }

        this.close();

        this.http.getChatSessionById(this.courseId, session.id).subscribe((session) => this.handleNewSession().next(session));
    }

    private closeAndStart() {
        this.close();
        if (this.sessionCreationIdentifier) {
            this.start();
        }
    }

    public currentSessionId(): Observable<number | undefined> {
        return this.sessionId$;
    }

    public currentMessages(): Observable<IrisMessage[]> {
        return this.messages.asObservable();
    }

    public currentStages(): Observable<IrisStageDTO[]> {
        return this.stages.asObservable();
    }

    public currentError(): Observable<IrisErrorMessageKey | undefined> {
        return this.error.asObservable();
    }

    public setCourseId(courseId: number): void {
        this.courseId = courseId;
    }

    public currentNumNewMessages(): Observable<number> {
        return this.numNewMessages.asObservable();
    }

    public currentSuggestions(): Observable<string[]> {
        return this.suggestions.asObservable();
    }

    public availableChatSessions(): Observable<IrisSession[]> {
        return this.chatSessions.asObservable();
    }
}
