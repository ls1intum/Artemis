import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { IrisAssistantMessage, IrisMessage, IrisSender, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { BehaviorSubject, Observable, Subject, Subscription, catchError, map, of, tap, throwError } from 'rxjs';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisChatWebsocketDTO, IrisChatWebsocketPayloadType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { UserService } from 'app/account/user/shared/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { Router } from '@angular/router';
import { captureException } from '@sentry/angular';
import dayjs from 'dayjs/esm';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { IrisMessageRequestDTO } from 'app/iris/shared/entities/iris-message-request-dto.model';
import { IrisMessageContentDTO } from 'app/iris/shared/entities/iris-message-content-dto.model';
import { randomInt } from 'app/shared/util/utils';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

export enum ChatServiceMode {
    TEXT_EXERCISE = 'TEXT_EXERCISE_CHAT',
    PROGRAMMING_EXERCISE = 'PROGRAMMING_EXERCISE_CHAT',
    COURSE = 'COURSE_CHAT',
    LECTURE = 'LECTURE_CHAT',
    TUTOR_SUGGESTION = 'TUTOR_SUGGESTION',
}

export interface SessionContext {
    mode: ChatServiceMode;
    entityId: number;
    entityName?: string;
}

/**
 * The IrisSessionService is responsible for managing Iris sessions and retrieving their associated messages.
 */
@Injectable({ providedIn: 'root' })
export class IrisChatService implements OnDestroy {
    private readonly irisChatHttpService = inject(IrisChatHttpService);
    private readonly irisWebsocketService = inject(IrisWebsocketService);
    private readonly irisStatusService = inject(IrisStatusService);
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

    private currentSessionIdSubject = new BehaviorSubject<number | undefined>(undefined);
    private currentSessionId$ = this.currentSessionIdSubject.asObservable();
    /** What the active session is about. Set when a session loads or is created. */
    private readonly _committedContext = signal<SessionContext | undefined>(undefined);
    readonly committedContext = this._committedContext.asReadonly();

    /** User's unsent override of {@link _committedContext}. Cleared on send (commits) or on revert. */
    private readonly _pendingContext = signal<SessionContext | undefined>(undefined);
    readonly displayContext = computed(() => this._pendingContext() ?? this._committedContext());

    /** Entity scope of the current page. Seeds {@link _pendingContext} when "New chat" starts a fresh session. */
    private readonly _pageContext = signal<SessionContext | undefined>(undefined);
    readonly pageContext = this._pageContext.asReadonly();

    public get sessionId(): number | undefined {
        return this.currentSessionIdSubject.value;
    }

    public set sessionId(id: number | undefined) {
        this.currentSessionIdSubject.next(id);
    }

    messages: BehaviorSubject<IrisMessage[]> = new BehaviorSubject([]);
    newIrisMessage: BehaviorSubject<IrisMessage | undefined> = new BehaviorSubject(undefined);
    numNewMessages: BehaviorSubject<number> = new BehaviorSubject(0);
    stages: BehaviorSubject<IrisStageDTO[]> = new BehaviorSubject([]);
    suggestions: BehaviorSubject<string[]> = new BehaviorSubject([]);
    citationInfo: BehaviorSubject<IrisCitationMetaDTO[]> = new BehaviorSubject([]);
    error: BehaviorSubject<IrisErrorMessageKey | undefined> = new BehaviorSubject(undefined);
    chatSessions: BehaviorSubject<IrisSessionDTO[]> = new BehaviorSubject([]);

    // Flips to true once the first session-load attempt has produced a result (success OR
    // error). Until then, `messages` still holds its empty initial value, so subscribers
    // that gate on "user has zero messages" (e.g. the Iris onboarding tour) cannot
    // distinguish "no messages yet" from "haven't loaded yet". Reset to false on close()
    // so a session switch re-arms the gate for the new session.
    private initialLoadCompleteSubject = new BehaviorSubject<boolean>(false);
    public initialLoadComplete$ = this.initialLoadCompleteSubject.asObservable();

    rateLimitInfo?: IrisRateLimitInformation;

    private rateLimitSubscription: Subscription;
    private acceptSubscription?: Subscription;
    private chatSessionSubscription?: Subscription;
    private chatSessionByIdSubscription?: Subscription;
    private sessionLoadingSubscription?: Subscription;
    private websocketSessionSubscription?: Subscription;
    private authenticationStateSubscription: Subscription;

    /**
     * Incremented every time {@link resetState} runs. HTTP/observable side effects that may complete
     * after a reset capture the generation at call time and short-circuit if it no longer matches,
     * preventing them from repopulating cleared state with the previous user's data.
     */
    private stateGeneration = 0;

    private shouldReopenChatSubject = new BehaviorSubject<boolean>(false);
    public shouldReopenChat$ = this.shouldReopenChatSubject.asObservable();

    private llmOptedOutSubject = new Subject<void>();
    public llmOptedOut$ = this.llmOptedOutSubject.asObservable();

    hasJustAcceptedLLMUsage = false;

    /**
     * This property should only be used internally in {@link getCourseId()} and {@link setCourseId()}.
     *
     * @deprecated do not use this property directly, use {@link getCourseId()} instead.
     */
    private courseId?: number;

    latestStartedSession?: IrisSessionDTO;

    private currentUserId?: number;

    protected constructor() {
        this.rateLimitSubscription = this.irisStatusService.currentRatelimitInfo().subscribe((info) => (this.rateLimitInfo = info));
        this.updateCourseId();
        // Seed the tracked user id from the already-authenticated identity so the initial replay
        // emission of getAuthenticationState() (a BehaviorSubject) does not trigger a no-op reset.
        this.currentUserId = this.accountService.userIdentity()?.id;
        // Reset all state when the authenticated user changes (logout or login as different user)
        // to prevent leaking the previous user's chat data into the new session.
        this.authenticationStateSubscription = this.accountService.getAuthenticationState().subscribe((user) => {
            if (this.currentUserId !== user?.id) {
                this.currentUserId = user?.id;
                this.resetState();
            }
        });
    }

    /**
     * Clears all in-memory chat state held by this service. Used on logout / user change to avoid leaking
     * the previous user's session data into the next user's view.
     *
     * Notes:
     * - Every BehaviorSubject is reset to its initial value unconditionally; we do not rely on
     *   {@link close} (which only clears most subjects when {@link sessionId} is set) because a
     *   future code path that populates a subject without setting sessionId would silently leak.
     * - {@link courseId} is not cleared because it is route-derived, not user-private; logout
     *   typically navigates away anyway so the URL extraction in {@link getCourseId} will refresh it.
     * - {@link stateGeneration} is incremented so any in-flight `tap`-style side effects on cold
     *   observables returned from {@link sendMessage}/{@link rateMessage}/{@link resendMessage}/
     *   {@link deleteSession} can detect the reset and skip their write-back.
     */
    private resetState(): void {
        this.stateGeneration++;
        // Tear down session-level subscriptions before clearing subjects so no late `next` can race.
        if (this.sessionId !== undefined) {
            this.irisWebsocketService.unsubscribeFromSession(this.sessionId);
        }
        this.websocketSessionSubscription?.unsubscribe();
        this.websocketSessionSubscription = undefined;
        this.chatSessionSubscription?.unsubscribe();
        this.chatSessionSubscription = undefined;
        this.chatSessionByIdSubscription?.unsubscribe();
        this.chatSessionByIdSubscription = undefined;
        this.sessionLoadingSubscription?.unsubscribe();
        this.sessionLoadingSubscription = undefined;
        this.acceptSubscription?.unsubscribe();
        this.acceptSubscription = undefined;
        // Reset every subject unconditionally.
        this.sessionId = undefined;
        this._committedContext.set(undefined);
        this._pendingContext.set(undefined);
        this._pageContext.set(undefined);
        this.messages.next([]);
        this.stages.next([]);
        this.suggestions.next([]);
        this.citationInfo.next([]);
        this.numNewMessages.next(0);
        this.newIrisMessage.next(undefined);
        this.error.next(undefined);
        this.chatSessions.next([]);
        this.shouldReopenChatSubject.next(false);
        // Plain fields.
        this.latestStartedSession = undefined;
        this.hasJustAcceptedLLMUsage = false;
        this.rateLimitInfo = undefined;
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
        this.sessionLoadingSubscription?.unsubscribe();
        this.websocketSessionSubscription?.unsubscribe();
        this.authenticationStateSubscription.unsubscribe();
    }

    protected start() {
        const sessionContext = this._committedContext();
        const requiresAcceptance = sessionContext ? this.modeRequiresLLMAcceptance.get(sessionContext.mode) : true;
        if (
            requiresAcceptance === false ||
            this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.LOCAL_AI ||
            this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.CLOUD_AI ||
            this.hasJustAcceptedLLMUsage
        ) {
            this.sessionLoadingSubscription?.unsubscribe();
            this.sessionLoadingSubscription = this.getCurrentSessionOrCreate().subscribe({
                ...this.handleNewSession(),
                complete: () => this.loadChatSessions(),
            });
        }
    }

    /**
     * Sends a message to the server and returns the created message.
     *
     * If the user has selected a different context via the dropdown since the last send
     * ({@link _pendingContext}), it is included in the request body so the server applies the
     * context switch atomically (CTXSWAP marker first, then the user message) in one round trip.
     *
     * @param message to be created
     * @param uncommittedFiles optional map of uncommitted file changes (path to content)
     */
    public sendMessage(message: string, uncommittedFiles: { [path: string]: string } = {}): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }

        // Trim messages (Spaces, newlines)
        message = message.trim();

        const contextToCommit = this._pendingContext();
        const pendingContextDTO = contextToCommit ? { mode: contextToCommit.mode, entityId: contextToCommit.entityId } : undefined;
        const requestDTO = new IrisMessageRequestDTO([IrisMessageContentDTO.text(message)], randomInt(), uncommittedFiles, pendingContextDTO);

        const generation = this.stateGeneration;
        return this.irisChatHttpService.createMessage(this.sessionId, requestDTO).pipe(
            tap((response: HttpResponse<IrisMessageResponseDTO>) => {
                if (this.stateGeneration !== generation) return;
                if (contextToCommit) {
                    this._committedContext.set(contextToCommit);
                    // Reflect the committed context in the sidebar entry immediately — without this,
                    // the related-entity icon/tooltip would stay stale until the next loadChatSessions().
                    const sessionId = this.sessionId;
                    const updatedSessions = this.chatSessions
                        .getValue()
                        .map((session) =>
                            session.id === sessionId
                                ? { ...session, mode: contextToCommit.mode, entityId: contextToCommit.entityId, entityName: contextToCommit.entityName ?? session.entityName }
                                : session,
                        );
                    this.chatSessions.next(updatedSessions);
                }
                this._pendingContext.set(undefined);
                this.suggestions.next([]);
                this.replaceOrAddMessage(this.mapMessageDTO(response.body!));
            }),
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                if (this.stateGeneration !== generation) return of(undefined);
                this.handleSendHttpError(error);
                return of(undefined);
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
        const generation = this.stateGeneration;
        return this.irisChatHttpService.createTutorSuggestion(this.sessionId).pipe(
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                if (this.stateGeneration !== generation) return of(undefined);
                this.handleSendHttpError(error);
                return of(undefined);
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

        const generation = this.stateGeneration;
        return this.irisChatHttpService.resendMessage(this.sessionId, message).pipe(
            map((r: HttpResponse<IrisMessageResponseDTO>) => this.mapMessageDTO(r.body!)),
            tap((m) => {
                if (this.stateGeneration !== generation) return;
                this.replaceMessage(m);
            }),
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                if (this.stateGeneration !== generation) return of();
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

        const generation = this.stateGeneration;
        return this.irisChatHttpService.rateMessage(this.sessionId, message.id!, !!helpful).pipe(
            map((r: HttpResponse<IrisMessageResponseDTO>) => this.mapMessageDTO(r.body!)),
            tap((m) => {
                if (this.stateGeneration !== generation) return;
                this.replaceMessage(m);
            }),
            map(() => undefined),
            catchError(() => {
                if (this.stateGeneration !== generation) return of(undefined);
                this.error.next(IrisErrorMessageKey.RATE_MESSAGE_FAILED);
                return of(undefined);
            }),
        );
    }

    public messagesRead(): void {
        this.numNewMessages.next(0);
        this.newIrisMessage.next(undefined);
    }

    public updateLLMUsageConsent(accepted: LLMSelectionDecision): void {
        if (accepted === LLMSelectionDecision.NO_AI) {
            this.hasJustAcceptedLLMUsage = false;
            this.acceptSubscription?.unsubscribe();
            this.acceptSubscription = this.userService.updateLLMSelectionDecision(accepted).subscribe({
                next: () => {
                    this.accountService.setUserLLMSelectionDecision(accepted);
                    this.llmOptedOutSubject.next();
                    this.close();
                },
                error: () => {
                    this.error.next(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE);
                    this.close();
                },
            });
            return;
        }
        this.acceptSubscription?.unsubscribe();
        this.acceptSubscription = this.userService.updateLLMSelectionDecision(accepted).subscribe({
            next: () => {
                this.hasJustAcceptedLLMUsage = true;
                this.accountService.setUserLLMSelectionDecision(accepted);
                this.closeAndStart();
            },
            error: () => {
                this.error.next(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE);
            },
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

    private updateChatSessions(updatedSessions: IrisSessionDTO[], includeLatestSession: boolean): void {
        if (includeLatestSession && this.latestStartedSession) {
            updatedSessions.unshift(this.latestStartedSession);
        }
        this.chatSessions.next(updatedSessions);
    }

    /**
     * @param latestSession the latest session that was started
     * @param currentSessions the currently displayed sessions in the history, expected to be sorted by creation date descending
     */
    private isLatestSessionIncludedInHistory(latestSession: IrisSessionDTO, currentSessions: IrisSessionDTO[] | undefined): boolean {
        const latestDisplayedSession: IrisSessionDTO | undefined = currentSessions?.[0];
        if (latestDisplayedSession === undefined) {
            return false;
        }

        const isSessionAlreadyDisplayed = latestDisplayedSession.id === latestSession.id;
        if (isSessionAlreadyDisplayed) {
            return true;
        }

        // noinspection UnnecessaryLocalVariableJS: not inlined because the variable name improves readability
        const isSessionAlreadyIncludedIfItContainsMessages = dayjs(latestSession.creationDate).isBefore(dayjs(latestDisplayedSession.creationDate));
        return isSessionAlreadyIncludedIfItContainsMessages;
    }

    /**
     * {@link IrisChatHttpService#getChatSessions} returns only sessions that have messages.
     *
     * As we open a new empty session without messages (e.g. when the dashboard is opened) we want to display this session in the history as well.
     */
    private addLatestEmptySessionToChatSessions(newIrisSession: IrisSession) {
        // Tutor-suggestion sessions have no chat mode and do not belong in the chat-history list.
        if (newIrisSession.mode === undefined) {
            return;
        }

        const currentSessions = this.chatSessions.getValue();

        const entityId = newIrisSession.entityId ?? this._committedContext()?.entityId;
        const newIrisSessionDTO: IrisSessionDTO = {
            id: newIrisSession.id,
            creationDate: newIrisSession.creationDate,
            mode: newIrisSession.mode,
            entityId: entityId,
            entityName: '',
            title: newIrisSession.title,
        };

        if (!this.isLatestSessionIncludedInHistory(newIrisSessionDTO, currentSessions)) {
            const shouldLatestSessionBeUpdated = this.sessionId === undefined || this.sessionId === newIrisSession.id;
            if (shouldLatestSessionBeUpdated) {
                this.latestStartedSession = newIrisSessionDTO;
            }
            this.updateChatSessions(currentSessions, true);
        }
    }

    /**
     * Updates the currently active chat context used by UI components.
     */
    private updateCurrentSessionContext(session: IrisSession | IrisSessionDTO): void {
        const chatMode = session.mode;
        const entityId = session.entityId ?? this._committedContext()?.entityId;
        if (chatMode !== undefined && entityId !== undefined) {
            this._committedContext.set({ mode: chatMode, entityId });
        }
    }

    private handleNewSession() {
        return {
            next: (newIrisSession: IrisSession) => {
                this.addLatestEmptySessionToChatSessions(newIrisSession);
                this.updateCurrentSessionContext(newIrisSession);

                this.sessionId = newIrisSession.id;
                this.citationInfo.next(newIrisSession.citationInfo || []);
                this.messages.next(newIrisSession.messages || []);
                this.parseLatestSuggestions(newIrisSession.latestSuggestions);
                // Flip the gate before subscribing to the websocket: the load itself has
                // succeeded, so consumers waiting on "messages have settled" are unblocked
                // even if the websocket layer throws synchronously (e.g. mocked-out in tests).
                this.initialLoadCompleteSubject.next(true);
                this.websocketSessionSubscription?.unsubscribe();
                this.websocketSessionSubscription = this.irisWebsocketService.subscribeToSession(this.sessionId).subscribe((message) => this.handleWebsocketMessage(message));
            },
            error: (error: IrisErrorMessageKey) => {
                this.error.next(error);
                // Even on failure, mark the load attempt as complete so consumers gating on
                // "messages have settled" don't wait forever (e.g. the onboarding tour would
                // never show up if a transient session-load error left the gate closed).
                this.initialLoadCompleteSubject.next(true);
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

    private handleWebsocketMessage(payload: IrisChatWebsocketDTO) {
        if (payload.rateLimitInfo) {
            this.irisStatusService.handleRateLimitInfo(payload.rateLimitInfo);
        }
        if (payload.sessionTitle && this.sessionId) {
            if (this.latestStartedSession?.id === this.sessionId) {
                this.latestStartedSession = { ...this.latestStartedSession, title: payload.sessionTitle };
            }

            // Update the observable list immutably so OnPush change detection picks up the new title immediately.
            const updatedSessions = this.chatSessions.getValue().map((session) => (session.id === this.sessionId ? { ...session, title: payload.sessionTitle } : session));
            this.chatSessions.next(updatedSessions);
        }
        if (payload.citationInfo?.length) {
            const merged = this.mergeCitationInfo(this.citationInfo.getValue(), payload.citationInfo);
            this.citationInfo.next(merged);
        }
        switch (payload.type) {
            case IrisChatWebsocketPayloadType.MESSAGE:
                if (payload.message?.sender === IrisSender.LLM) {
                    this.numNewMessages.next(this.numNewMessages.getValue() + 1);
                }
                if (payload.message?.id) {
                    this.replaceOrAddMessage(this.mapMessageDTO(payload.message));
                }
                if (payload.stages) {
                    this.stages.next(this.filterStages(payload.stages));
                }
                break;
            case IrisChatWebsocketPayloadType.STATUS:
                this.stages.next(this.filterStages(payload.stages || []));
                if (payload.suggestions) {
                    this.suggestions.next(payload.suggestions);
                }
                break;
        }
    }

    private mapMessageDTO(dto: IrisMessageResponseDTO): IrisMessage {
        return Object.assign({}, dto, {
            sentAt: dto.sentAt ? dayjs(dto.sentAt) : undefined,
        }) as IrisMessage;
    }

    private filterStages(stages: IrisStageDTO[]): IrisStageDTO[] {
        return stages.filter((stage) => !stage.internal);
    }

    protected close(): void {
        if (this.sessionId) {
            this.irisWebsocketService.unsubscribeFromSession(this.sessionId);
            this.websocketSessionSubscription?.unsubscribe();
            this.websocketSessionSubscription = undefined;
            this.sessionId = undefined;
            this.messages.next([]);
            this.stages.next([]);
            this.suggestions.next([]);
            this.citationInfo.next([]);
            this.numNewMessages.next(0);
            this.newIrisMessage.next(undefined);
            this.initialLoadCompleteSubject.next(false);
        }
        // Always clear the pending context, even when no session existed yet: a pending context
        // may have been staged by the lecture/exercise auto-preselect before the session loaded.
        this._pendingContext.set(undefined);
        this.error.next(undefined);
    }

    /**
     * Retrieves the current session or creates a new one if it doesn't exist.
     */
    private getCurrentSessionOrCreate(): Observable<IrisSession> {
        const sessionContext = this._committedContext();
        if (!sessionContext) {
            throw new Error('Session context not set');
        }

        return this.irisChatHttpService.getCurrentSessionOrCreateIfNotExists(sessionContext.mode, sessionContext.entityId).pipe(
            map((response: HttpResponse<IrisSession>) => {
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
        const latestStartedSession = this.latestStartedSession;
        if (courseId) {
            this.chatSessionSubscription?.unsubscribe();
            this.chatSessionSubscription = this.irisChatHttpService.getChatSessions(courseId).subscribe((sessions: IrisSessionDTO[]) => {
                const sessionsWithMessages = sessions ?? [];
                if (latestStartedSession && !this.isLatestSessionIncludedInHistory(latestStartedSession, sessionsWithMessages)) {
                    this.updateChatSessions(sessionsWithMessages, true);
                } else {
                    this.updateChatSessions(sessionsWithMessages, false);
                }
            });
        } else {
            captureException(new Error('Could not load chat sessions, courseId is not set.'), {
                extra: {
                    currentUrl: this.router.url,
                    userId: this.accountService.userIdentity()?.id,
                    sessionContext: this._committedContext(),
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
    private createNewSession(): Observable<IrisSession> {
        const sessionContext = this._committedContext();
        if (!sessionContext) {
            throw new Error('Session context not set');
        }
        return this.irisChatHttpService.createSession(sessionContext.mode, sessionContext.entityId).pipe(
            map((response: HttpResponse<IrisSession>) => {
                if (response.body) {
                    return response.body;
                } else {
                    throw new Error(IrisErrorMessageKey.SESSION_CREATION_FAILED);
                }
            }),
            catchError(() => throwError(() => new Error(IrisErrorMessageKey.SESSION_CREATION_FAILED))),
        );
    }
    /**
     * Commits `(mode, entityId)` and opens its session, clearing any pending context.
     * Skips the session reload if the context is already committed.
     */
    private resumeOrCreateChat(mode: ChatServiceMode, entityId: number): void {
        const current = this._committedContext();
        const isDifferent = current?.mode !== mode || current?.entityId !== entityId;
        this._committedContext.set({ mode, entityId });
        this._pendingContext.set(undefined);
        if (isDifferent) {
            this.closeAndStart();
        }
    }

    /**
     * Course-dashboard mount. No-op if the dashboard is re-mounted for the same course.
     */
    public resumeOrCreateCourseChat(courseId: number): void {
        this._pageContext.set({ mode: ChatServiceMode.COURSE, entityId: courseId });
        this.resumeOrCreateChat(ChatServiceMode.COURSE, courseId);
    }

    /**
     * Tutor-suggestion entry point (e.g. from a communication thread). The TUTOR_SUGGESTION mode
     * bypasses LLM-consent gating in {@link start} via {@link modeRequiresLLMAcceptance}.
     */
    public resumeOrCreateTutorSuggestionChat(postId: number): void {
        this.resumeOrCreateChat(ChatServiceMode.TUTOR_SUGGESTION, postId);
    }

    /**
     * Lecture/exercise page mount: resumes a history session tagged with `(mode, entityId)`
     * if one exists, otherwise opens a fresh COURSE chat with the context staged via
     * {@link stagePendingContext}. Always re-fetches sessions so URL-direct entry behaves
     * identically to in-app navigation. The optional `entityName` is stored on the
     * {@link _pageContext} so the "New chat" affordance can later stage a labelled chip.
     */
    public openChatForContext(mode: ChatServiceMode, entityId: number, entityName?: string): void {
        this._pageContext.set({ mode, entityId, entityName });
        const courseId = this.getCourseId();
        if (courseId === undefined) return;

        // Cancel any in-flight session-loading effort so racing navigations don't double-fire.
        this.sessionLoadingSubscription?.unsubscribe();
        const generation = this.stateGeneration;

        this.sessionLoadingSubscription = this.irisChatHttpService.getChatSessions(courseId).subscribe((sessions: IrisSessionDTO[]) => {
            if (this.stateGeneration !== generation) return;
            // switchToSession itself does not re-query getChatSessions, so the sidebar must be pre-populated here.
            this.chatSessions.next(sessions);
            const matching = sessions.find((s) => s.mode === mode && s.entityId === entityId);
            if (matching) {
                this.switchToSession(matching);
                return;
            }
            this.startFreshChat();
            this.stagePendingContext(mode, entityId, entityName);
        });
    }

    /**
     * Stages a context override; the server only sees it on the next {@link sendMessage},
     * which commits it via a CTXSWAP marker. Reverting to the committed context clears the override.
     * Safe to call before {@link sessionId} is set (e.g. during lecture/exercise auto-preselect).
     */
    public stagePendingContext(mode: ChatServiceMode, entityId: number, entityName?: string): void {
        const committed = this._committedContext();
        if (committed?.mode === mode && committed?.entityId === entityId) {
            this._pendingContext.set(undefined);
        } else {
            this._pendingContext.set({ mode, entityId, entityName });
        }
    }

    /**
     * Closes the active session and opens a fresh COURSE session for the current course.
     * No-op if the current session is already an empty COURSE session.
     */
    public startFreshChat(): void {
        const committed = this._committedContext();
        const courseId = this.getCourseId();
        const isFreshCourseSession =
            this.sessionId !== undefined && committed?.mode === ChatServiceMode.COURSE && committed.entityId === courseId && this.messages.getValue().length === 0;
        if (!isFreshCourseSession && courseId) {
            this.close();
            this._committedContext.set({ mode: ChatServiceMode.COURSE, entityId: courseId });
            this.sessionLoadingSubscription?.unsubscribe();
            this.sessionLoadingSubscription = this.createNewSession().subscribe({
                ...this.handleNewSession(),
                complete: () => this.loadChatSessions(),
            });
        }
    }

    switchToSession(session: IrisSessionDTO): void {
        if (this.sessionId === session.id) {
            return;
        }

        this.close();

        const courseId = this.getCourseId();
        const chatMode = session.mode;
        const entityId = session.entityId;
        this._committedContext.set(chatMode && entityId ? { mode: chatMode, entityId } : undefined);
        if (courseId) {
            this.chatSessionByIdSubscription?.unsubscribe();
            this.chatSessionByIdSubscription = this.irisChatHttpService.getChatSessionById(courseId, session.id).subscribe((session) => {
                this.handleNewSession().next(session);
            });
        } else {
            captureException(new Error('Could not switch session, courseId is not set.'), {
                extra: {
                    currentUrl: this.router.url,
                    userId: this.accountService.userIdentity()?.id,
                    sessionId: this.sessionId,
                    sessionContext: this._committedContext(),
                },
                tags: {
                    category: 'Iris',
                },
            });
        }
    }

    private closeAndStart() {
        this.close();
        if (this._committedContext()) {
            this.start();
        }
    }

    public currentSessionId(): Observable<number | undefined> {
        return this.currentSessionId$;
    }

    public currentMessages(): Observable<IrisMessage[]> {
        return this.messages.asObservable();
    }

    public currentStages(): Observable<IrisStageDTO[]> {
        return this.stages.asObservable();
    }

    public currentCitationInfo(): Observable<IrisCitationMetaDTO[]> {
        return this.citationInfo.asObservable();
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
        // eslint-disable-next-line @typescript-eslint/no-deprecated -- usage in getter is okay
        if (this.courseId) {
            // eslint-disable-next-line @typescript-eslint/no-deprecated -- usage in getter is okay
            return this.courseId;
        }

        return this.updateCourseId();
    }

    public setCourseId(courseId: number | undefined): void {
        // eslint-disable-next-line @typescript-eslint/no-deprecated -- usage in setter is okay
        this.courseId = courseId;
        if (courseId) {
            this.irisStatusService.setCurrentCourse(courseId);
        }
    }

    private mergeCitationInfo(existing: IrisCitationMetaDTO[], incoming: IrisCitationMetaDTO[]): IrisCitationMetaDTO[] {
        const merged = new Map<number, IrisCitationMetaDTO>();
        existing.forEach((citation) => {
            merged.set(citation.entityId, citation);
        });
        incoming.forEach((citation) => {
            merged.set(citation.entityId, citation);
        });
        return Array.from(merged.values());
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

    /**
     * Deletes a single chat session by ID.
     * Removes it from the local session list and switches to another session if the deleted one was active.
     * @param sessionId the ID of the session to delete
     */
    public deleteSession(sessionId: number): Observable<void> {
        const generation = this.stateGeneration;
        return this.irisChatHttpService.deleteSession(sessionId).pipe(
            tap(() => {
                if (this.stateGeneration !== generation) return;
                const currentSessions = this.chatSessions.getValue().filter((s) => s.id !== sessionId);
                if (this.latestStartedSession?.id === sessionId) {
                    this.latestStartedSession = undefined;
                }
                this.chatSessions.next(currentSessions);

                if (this.sessionId === sessionId) {
                    this.close();
                    if (currentSessions.length > 0) {
                        this.switchToSession(currentSessions[0]);
                    }
                    // When no sessions remain, just stay in the closed state.
                    // The user can start a new session manually via the "New chat" button.
                }
            }),
            map(() => undefined),
        );
    }

    /**
     * Sets whether the chat should reopen after being closed by LLM selection modal.
     */
    public setShouldReopenChat(value: boolean): void {
        this.shouldReopenChatSubject.next(value);
    }
}
