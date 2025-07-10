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
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { Router } from '@angular/router';
import { captureException } from '@sentry/angular';

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
    private readonly router = inject(Router);

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
    chatSessions: BehaviorSubject<IrisSessionDTO[]> = new BehaviorSubject([]);

    rateLimitInfo?: IrisRateLimitInformation;

    private rateLimitSubscription: Subscription;
    private acceptSubscription?: Subscription;
    private chatSessionSubscription?: Subscription;
    private chatSessionByIdSubscription?: Subscription;

    private sessionCreationIdentifier?: string;

    hasJustAcceptedExternalLLMUsage = false;

    /**
     * This property should only be used internally in {@link getCourseId()} and {@link setCourseId()}.
     *
     * @deprecated do not use this property directly, use {@link getCourseId()} instead.
     */
    private courseId?: number;

    protected constructor() {
        this.rateLimitSubscription = this.status.currentRatelimitInfo().subscribe((info) => (this.rateLimitInfo = info));
        this.updateCourseId();
    }

    /**
     * <b>Extracts the course ID from the current route URL.</b>
     *
     * <p>We assume the route follows the structure:</p>
     * <pre>
     * /courses/{courseId}/lectures/{lectureId}
     * </pre>
     *
     * <p>For example:</p>
     * <ul>
     *   <li><code>/courses/19/lectures/27</code> - Extracts <code>19</code> as the course ID.</li>
     * </ul>
     *
     *
     * @return courseId retrieved from current route or <code>undefined</code> if the route does not match the expected structure
     *
     * @Note We cannot use ActivatedRoute here, because this service is injectable in the root
     *       and therefore might be instantiated before the route is fully initialized.
     */
    private getCourseIdFromCurrentUrl(): number | undefined {
        const currentUrl = this.router.url;

        /**
         * Regex to match '/courses/{number}'
         */
        const COURSE_ID_REGEX = /\/courses\/(\d+)/;
        const match = currentUrl.match(COURSE_ID_REGEX);

        /**
         * 0 would contain the fully matched string, e.g. '/courses/19'
         *
         * 1 is the first capturing group, which contains the course ID, e.g. '19'
         */
        const CAPTURING_GROUP_INDEX = 1;
        return match ? Number(match[CAPTURING_GROUP_INDEX]) : undefined;
    }

    private updateCourseId(): number | undefined {
        const updatedCourseId = this.getCourseIdFromCurrentUrl();
        this.setCourseId(updatedCourseId);
        return updatedCourseId;
    }

    ngOnDestroy(): void {
        this.rateLimitSubscription.unsubscribe();
        this.acceptSubscription?.unsubscribe();
        this.chatSessionSubscription?.unsubscribe();
        this.chatSessionByIdSubscription?.unsubscribe();
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
            next: (newIrisSession: IrisSession) => {
                const currentSessions = this.chatSessions.getValue();
                const isNewSessionIncludedInHistory = !currentSessions.some((session) => session.id === newIrisSession.id);
                if (!isNewSessionIncludedInHistory) {
                    this.chatSessions.next([newIrisSession, ...currentSessions]);
                }

                this.sessionId = newIrisSession.id;
                this.messages.next(newIrisSession.messages || []);
                this.parseLatestSuggestions(newIrisSession.latestSuggestions);
                this.ws.subscribeToSession(this.sessionId).subscribe((m) => this.handleWebsocketMessage(m));
            },
            error: (e: IrisErrorMessageKey) => {
                this.error.next(e as IrisErrorMessageKey);
            },
        };
    }

    /**
     * Parses the latest suggestions string and updates the suggestions subject.
     *
     * @param str The latest suggestions string
     */
    private parseLatestSuggestions(str?: string) {
        if (!str) {
            this.suggestions.next([]);
            return;
        }

        const suggestions = JSON.parse(str);
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
        const courseId = this.getCourseId();
        if (courseId) {
            this.chatSessionSubscription?.unsubscribe();
            this.chatSessionSubscription = this.http.getChatSessions(courseId).subscribe((sessions: IrisSessionDTO[]) => {
                this.chatSessions.next(sessions ?? []);
            });
        } else {
            captureException(new Error('Could not load chat sessions, courseId is not set.'), {
                extra: {
                    currentUrl: this.router.url,
                    userId: this.accountService.userIdentity?.id,
                    sessionCreationIdentifier: this.sessionCreationIdentifier,
                },
                tags: {
                    category: 'Iris',
                },
            });
            this.chatSessions.next([]);
        }
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

    switchToSession(session: IrisSessionDTO): void {
        if (this.sessionId === session.id) {
            return;
        }

        this.close();

        const courseId = this.getCourseId();
        if (courseId) {
            this.chatSessionByIdSubscription?.unsubscribe();
            this.chatSessionByIdSubscription = this.http.getChatSessionById(courseId, session.id).subscribe((session) => this.handleNewSession().next(session));
        } else {
            captureException(new Error('Could not switch session, courseId is not set.'), {
                extra: {
                    currentUrl: this.router.url,
                    userId: this.accountService.userIdentity?.id,
                    sessionId: this.sessionId,
                    sessionCreationIdentifier: this.sessionCreationIdentifier,
                },
                tags: {
                    category: 'Iris',
                },
            });
        }
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

    /**
     * <b>Ensures that the {@link courseId} is always available when accessed.</b>
     *
     * <p>Since this service is injectable in the root, it might be instantiated before the route is fully initialized,
     * and therefore the {@link courseId} might not yet be set. To address this, this getter wraps the {@link courseId}
     * and triggers an update via {@link updateCourseId} if it is not already set.</p>
     *
     * <p>Required in edge cases where a route requiring the {@link courseId} (e.g., a lecture from the student view)
     * is loaded directly by accessing the link or by reloading the page.</p>
     */
    public getCourseId(): number | undefined {
        if (this.courseId) {
            return this.courseId;
        }

        return this.updateCourseId();
    }

    public setCourseId(courseId: number | undefined): void {
        this.courseId = courseId;
    }

    public currentNumNewMessages(): Observable<number> {
        return this.numNewMessages.asObservable();
    }

    public currentSuggestions(): Observable<string[]> {
        return this.suggestions.asObservable();
    }

    public availableChatSessions(): Observable<IrisSessionDTO[]> {
        return this.chatSessions.asObservable();
    }
}
