import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, EMPTY, Observable, Subject, Subscription, catchError, distinctUntilChanged, map, of, switchMap, takeUntil, tap, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { captureException } from '@sentry/angular';
import dayjs from 'dayjs/esm';

import { IrisChatHttpService, Response } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisAssistantMessage, IrisMessage, IrisSender, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisMessageRequestDTO } from 'app/iris/shared/entities/iris-message-request-dto.model';
import { IrisMessageContentDTO } from 'app/iris/shared/entities/iris-message-content-dto.model';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { IrisExerciseChatSession } from 'app/iris/shared/entities/iris-exercise-chat-session.model';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { IrisChatWebsocketDTO, IrisChatWebsocketPayloadType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { IrisStatusDTO } from 'app/iris/shared/entities/iris-health.model';
import { ChatServiceMode, chatModeToUrlComponent } from 'app/iris/shared/entities/iris-chat-mode.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { randomInt } from 'app/shared/util/utils';

/**
 * Component-scoped controller for an Iris chat host. Owns chat session state, status (heartbeat,
 * rate limit), and the websocket subscription. Lifetime equals the host component — when the host
 * is destroyed, all subscriptions, signals, and the websocket handle are released. This is what
 * makes cross-user state leaks structurally impossible (unlike a `providedIn: 'root'` singleton).
 */
@Injectable()
export class IrisChatControllerService {
    private static readonly HEARTBEAT_INTERVAL_MS = 60 * 1000 * 5;

    private readonly irisChatHttpService = inject(IrisChatHttpService);
    private readonly httpClient = inject(HttpClient);
    private readonly websocketService = inject(WebsocketService);
    private readonly profileService = inject(ProfileService);
    private readonly userService = inject(UserService);
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);

    private readonly modeRequiresLLMAcceptance = new Map<ChatServiceMode, boolean>([
        [ChatServiceMode.TEXT_EXERCISE, true],
        [ChatServiceMode.PROGRAMMING_EXERCISE, true],
        [ChatServiceMode.COURSE, true],
        [ChatServiceMode.LECTURE, true],
        [ChatServiceMode.TUTOR_SUGGESTION, false],
    ]);

    private readonly currentSessionIdSubject = new BehaviorSubject<number | undefined>(undefined);
    private readonly currentRelatedEntityIdSubject = new BehaviorSubject<number | undefined>(undefined);
    private readonly currentChatModeSubject = new BehaviorSubject<ChatServiceMode | undefined>(undefined);
    private readonly shouldReopenChatSubject = new BehaviorSubject<boolean>(false);

    readonly messages = new BehaviorSubject<IrisMessage[]>([]);
    readonly newIrisMessage = new BehaviorSubject<IrisMessage | undefined>(undefined);
    readonly numNewMessages = new BehaviorSubject<number>(0);
    readonly stages = new BehaviorSubject<IrisStageDTO[]>([]);
    readonly suggestions = new BehaviorSubject<string[]>([]);
    readonly citationInfo = new BehaviorSubject<IrisCitationMetaDTO[]>([]);
    readonly error = new BehaviorSubject<IrisErrorMessageKey | undefined>(undefined);
    readonly chatSessions = new BehaviorSubject<IrisSessionDTO[]>([]);

    readonly shouldReopenChat$ = this.shouldReopenChatSubject.asObservable();

    private readonly courseIdInternal = signal<number | undefined>(undefined);
    readonly courseId = this.courseIdInternal.asReadonly();

    // Public so tests can drive specific values; in production, only the controller mutates these
    // through checkHeartbeat / handleWebsocketMessage.
    readonly activeSubject = new BehaviorSubject<boolean>(false);
    readonly currentRatelimitInfoSubject = new BehaviorSubject<IrisRateLimitInformation>(new IrisRateLimitInformation(0, 0, 0));
    private isActive = false;
    private wsDisconnected = false;
    private heartbeatIntervalId: ReturnType<typeof setInterval> | undefined;

    rateLimitInfo?: IrisRateLimitInformation;
    hasJustAcceptedLLMUsage = false;
    latestStartedSession?: IrisSessionDTO;

    private sessionCreationIdentifier?: string;
    // Track the active mode directly. The previous design fished it out of the URL fragment
    // (e.g. `tutor-suggestion/1`) by string-includes against enum values (e.g. `TUTOR_SUGGESTION`),
    // which never matched and silently broke the LLM-acceptance bypass for tutor-suggestion mode.
    private activeChatMode?: ChatServiceMode;
    private acceptSubscription?: Subscription;
    private chatSessionSubscription?: Subscription;
    private chatSessionByIdSubscription?: Subscription;

    // Fires inside close() to cancel any in-flight context-bound HTTP work (session-fetches,
    // session-creates, chat-history loads). Without this, a context switch during an in-flight
    // request would let the late response repopulate state for the OLD context, corrupting the
    // newly-started one. takeUntilDestroyed only fires on host destroy — not enough granularity.
    private readonly contextSwitch$ = new Subject<void>();

    constructor() {
        this.currentRatelimitInfoSubject.pipe(takeUntilDestroyed()).subscribe((info) => (this.rateLimitInfo = info));

        // Single websocket subscription chain: switching session ID auto-cancels the prior subscription
        // via switchMap. The null-handling MUST be inside switchMap (not in a `filter` before it),
        // otherwise `currentSessionIdSubject.next(undefined)` emissions would be swallowed and the
        // prior STOMP subscription would leak until another session id arrived.
        this.currentSessionIdSubject
            .pipe(
                distinctUntilChanged(),
                switchMap((id) => (id ? this.websocketService.subscribe<IrisChatWebsocketDTO>(`/user/topic/iris/${id}`) : EMPTY)),
                takeUntilDestroyed(),
            )
            .subscribe((payload) => this.handleWebsocketMessage(payload));

        if (this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS)) {
            this.websocketService.connectionState.pipe(takeUntilDestroyed()).subscribe((status) => {
                this.wsDisconnected = !status.connected && status.wasEverConnectedBefore;
            });
            this.heartbeatIntervalId = setInterval(() => this.checkHeartbeat(), IrisChatControllerService.HEARTBEAT_INTERVAL_MS);
            this.destroyRef.onDestroy(() => {
                if (this.heartbeatIntervalId !== undefined) {
                    clearInterval(this.heartbeatIntervalId);
                }
                this.acceptSubscription?.unsubscribe();
                this.chatSessionSubscription?.unsubscribe();
                this.chatSessionByIdSubscription?.unsubscribe();
                this.contextSwitch$.complete();
            });
        } else {
            this.destroyRef.onDestroy(() => {
                this.acceptSubscription?.unsubscribe();
                this.chatSessionSubscription?.unsubscribe();
                this.chatSessionByIdSubscription?.unsubscribe();
                this.contextSwitch$.complete();
            });
        }
    }

    get sessionId(): number | undefined {
        return this.currentSessionIdSubject.value;
    }

    set sessionId(id: number | undefined) {
        this.currentSessionIdSubject.next(id);
    }

    /**
     * Establishes the chat host's context. Called by the host on construction (and again whenever
     * the host's relevant identifiers change, e.g. an exercise split panel switching exercises).
     * @throws if courseId is undefined; preserves the diagnostic value of the previous
     * loadChatSessions exception path while surfacing the failure earlier.
     */
    setContext(courseId: number | undefined, mode?: ChatServiceMode, entityId?: number): void {
        if (courseId === undefined) {
            const error = new Error('IrisChatControllerService.setContext called without a courseId');
            captureException(error, {
                extra: { mode, entityId, currentUrl: this.router.url, userId: this.accountService.userIdentity()?.id },
                tags: { category: 'Iris' },
            });
            throw error;
        }

        const courseChanged = this.courseIdInternal() !== courseId;
        this.courseIdInternal.set(courseId);

        const modeUrl = mode ? chatModeToUrlComponent(mode) : undefined;
        const newIdentifier = modeUrl && entityId ? `${modeUrl}/${entityId}` : undefined;
        const identifierChanged = this.sessionCreationIdentifier !== newIdentifier;
        this.sessionCreationIdentifier = newIdentifier;
        this.activeChatMode = newIdentifier ? mode : undefined;

        if (courseChanged) {
            // The chat-history list is per-course, so a course change must clear it; otherwise
            // loadChatSessions would briefly show the previous course's history before the new
            // request resolves. Same applies to latestStartedSession (a per-course pointer).
            this.chatSessions.next([]);
            this.latestStartedSession = undefined;
            this.checkHeartbeat();
        }

        if (identifierChanged) {
            this.closeAndStart();
        }
    }

    switchToNewSession(mode: ChatServiceMode, id?: number): void {
        const modeUrl = chatModeToUrlComponent(mode);
        this.sessionCreationIdentifier = modeUrl && id ? `${modeUrl}/${id}` : undefined;
        this.activeChatMode = this.sessionCreationIdentifier ? mode : undefined;
        this.close();
        if (this.sessionCreationIdentifier) {
            this.createNewSession()
                .pipe(takeUntil(this.contextSwitch$), takeUntilDestroyed(this.destroyRef))
                .subscribe({
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

        const courseId = this.courseIdInternal();
        const entityId = session.entityId;
        const chatMode = session.chatMode;
        const modeUrl = chatModeToUrlComponent(chatMode);
        this.sessionCreationIdentifier = modeUrl && entityId ? `${modeUrl}/${entityId}` : undefined;
        this.activeChatMode = this.sessionCreationIdentifier ? chatMode : undefined;
        if (courseId) {
            this.chatSessionByIdSubscription?.unsubscribe();
            this.chatSessionByIdSubscription = this.irisChatHttpService
                .getChatSessionById(courseId, session.id)
                .pipe(takeUntil(this.contextSwitch$), takeUntilDestroyed(this.destroyRef))
                .subscribe((loadedSession) => {
                    this.currentChatModeSubject.next(chatMode);
                    this.currentRelatedEntityIdSubject.next(entityId);
                    this.handleNewSession().next(loadedSession);
                });
        } else {
            captureException(new Error('Could not switch session, courseId is not set.'), {
                extra: {
                    currentUrl: this.router.url,
                    userId: this.accountService.userIdentity()?.id,
                    sessionId: this.sessionId,
                    sessionCreationIdentifier: this.sessionCreationIdentifier,
                },
                tags: { category: 'Iris' },
            });
        }
    }

    clearChat(): void {
        this.close();
        this.createNewSession()
            .pipe(takeUntil(this.contextSwitch$), takeUntilDestroyed(this.destroyRef))
            .subscribe({
                ...this.handleNewSession(),
                complete: () => this.loadChatSessions(),
            });
    }

    sendMessage(message: string, uncommittedFiles: { [path: string]: string } = {}): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }

        const requestDTO = new IrisMessageRequestDTO([IrisMessageContentDTO.text(message.trim())], randomInt(), uncommittedFiles);

        return this.irisChatHttpService.createMessage(this.sessionId, requestDTO).pipe(
            tap((response: HttpResponse<IrisMessageResponseDTO>) => {
                this.suggestions.next([]);
                this.replaceOrAddMessage(this.mapMessageDTO(response.body!));
            }),
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                this.handleSendHttpError(error);
                return of(undefined);
            }),
        );
    }

    requestTutorSuggestion(): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }
        return this.irisChatHttpService.createTutorSuggestion(this.sessionId).pipe(
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                this.handleSendHttpError(error);
                return of(undefined);
            }),
        );
    }

    resendMessage(message: IrisUserMessage): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }

        return this.irisChatHttpService.resendMessage(this.sessionId, message).pipe(
            map((r: HttpResponse<IrisMessageResponseDTO>) => this.mapMessageDTO(r.body!)),
            tap((m) => {
                this.replaceMessage(m);
            }),
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                this.handleSendHttpError(error);
                return of();
            }),
        );
    }

    rateMessage(message: IrisAssistantMessage, helpful?: boolean): Observable<undefined> {
        if (!this.sessionId) {
            return throwError(() => new Error('Not initialized'));
        }

        return this.irisChatHttpService.rateMessage(this.sessionId, message.id!, !!helpful).pipe(
            map((r: HttpResponse<IrisMessageResponseDTO>) => this.mapMessageDTO(r.body!)),
            tap((m) => this.replaceMessage(m)),
            map(() => undefined),
            catchError(() => {
                this.error.next(IrisErrorMessageKey.RATE_MESSAGE_FAILED);
                return of(undefined);
            }),
        );
    }

    messagesRead(): void {
        this.numNewMessages.next(0);
        this.newIrisMessage.next(undefined);
    }

    updateLLMUsageConsent(accepted: LLMSelectionDecision): void {
        this.acceptSubscription?.unsubscribe();
        if (accepted === LLMSelectionDecision.NO_AI) {
            this.hasJustAcceptedLLMUsage = false;
            this.acceptSubscription = this.userService.updateLLMSelectionDecision(accepted).subscribe({
                next: () => {
                    this.accountService.setUserLLMSelectionDecision(accepted);
                    this.close();
                },
                error: () => {
                    this.error.next(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE);
                    this.close();
                },
            });
            return;
        }
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

    deleteSession(sessionId: number): Observable<void> {
        return this.irisChatHttpService.deleteSession(sessionId).pipe(
            tap(() => {
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
                    // When no sessions remain, stay closed. The user can start a new session manually via the "New chat" button.
                }
            }),
            map(() => undefined),
        );
    }

    setShouldReopenChat(value: boolean): void {
        this.shouldReopenChatSubject.next(value);
    }

    currentSessionId(): Observable<number | undefined> {
        return this.currentSessionIdSubject.asObservable();
    }

    currentRelatedEntityId(): Observable<number | undefined> {
        return this.currentRelatedEntityIdSubject.asObservable();
    }

    currentChatMode(): Observable<ChatServiceMode | undefined> {
        return this.currentChatModeSubject.asObservable();
    }

    currentMessages(): Observable<IrisMessage[]> {
        return this.messages.asObservable();
    }

    currentStages(): Observable<IrisStageDTO[]> {
        return this.stages.asObservable();
    }

    currentCitationInfo(): Observable<IrisCitationMetaDTO[]> {
        return this.citationInfo.asObservable();
    }

    currentError(): Observable<IrisErrorMessageKey | undefined> {
        return this.error.asObservable();
    }

    currentNumNewMessages(): Observable<number> {
        return this.numNewMessages.asObservable();
    }

    currentSuggestions(): Observable<string[]> {
        return this.suggestions.asObservable();
    }

    availableChatSessions(): Observable<IrisSessionDTO[]> {
        return this.chatSessions.asObservable();
    }

    getActiveStatus(): Observable<boolean> {
        return this.activeSubject.asObservable();
    }

    currentRatelimitInfo(): Observable<IrisRateLimitInformation> {
        return this.currentRatelimitInfoSubject.asObservable();
    }

    private start(): void {
        const requiresAcceptance = this.activeChatMode === undefined ? true : (this.modeRequiresLLMAcceptance.get(this.activeChatMode) ?? true);
        const llmDecision = this.accountService.userIdentity()?.selectedLLMUsage;
        if (requiresAcceptance === false || llmDecision === LLMSelectionDecision.LOCAL_AI || llmDecision === LLMSelectionDecision.CLOUD_AI || this.hasJustAcceptedLLMUsage) {
            this.getCurrentSessionOrCreate()
                .pipe(takeUntil(this.contextSwitch$), takeUntilDestroyed(this.destroyRef))
                .subscribe({
                    ...this.handleNewSession(),
                    complete: () => this.loadChatSessions(),
                });
        }
    }

    private close(): void {
        // Cancel any in-flight context-bound HTTP work BEFORE we touch local state. If a
        // session-fetch or session-create from the previous context is still pending, its
        // response would otherwise land after the new context starts and corrupt it.
        this.contextSwitch$.next();
        this.chatSessionSubscription?.unsubscribe();
        this.chatSessionByIdSubscription?.unsubscribe();
        if (this.sessionId) {
            this.sessionId = undefined;
            this.currentRelatedEntityIdSubject.next(undefined);
            this.currentChatModeSubject.next(undefined);
            this.messages.next([]);
            this.stages.next([]);
            this.suggestions.next([]);
            this.citationInfo.next([]);
            this.numNewMessages.next(0);
            this.newIrisMessage.next(undefined);
            // latestStartedSession is the "session I just started" pointer; once we close it,
            // the pointer is stale — the next handleNewSession will set a fresh one. Without
            // this reset, switching exercises within a course can prepend the old exercise's
            // session into the new exercise's history.
            this.latestStartedSession = undefined;
        }
        this.error.next(undefined);
    }

    private closeAndStart(): void {
        this.close();
        if (this.sessionCreationIdentifier) {
            this.start();
        }
    }

    private replaceOrAddMessage(message: IrisMessage): void {
        const messageWasReplaced = this.replaceMessage(message);
        if (!messageWasReplaced) {
            if (message.sender === IrisSender.LLM) {
                this.newIrisMessage.next(message);
            }
            this.messages.next([...this.messages.getValue(), message]);
        }
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
                this.addLatestEmptySessionToChatSessions(newIrisSession);
                this.updateCurrentSessionContext(newIrisSession);

                // Order matters: set messages/citations BEFORE sessionId. Setting sessionId
                // triggers the websocket subscription via switchMap. With a synchronous source
                // (e.g. tests using `of(...)`), the first message arrives during the next
                // statement — if `messages.next(...)` runs after, it would overwrite that
                // message. The legacy service avoided this by subscribing to the websocket
                // explicitly at the end of this block; the new architecture ties it to
                // sessionId, so we reorder instead.
                this.citationInfo.next(newIrisSession.citationInfo || []);
                this.messages.next(newIrisSession.messages || []);
                this.parseLatestSuggestions(newIrisSession.latestSuggestions);
                this.sessionId = newIrisSession.id;
            },
            error: (error: IrisErrorMessageKey) => {
                this.error.next(error);
            },
        };
    }

    private addLatestEmptySessionToChatSessions(newIrisSession: IrisSession): void {
        const currentSessions = this.chatSessions.getValue();

        const chatMode = newIrisSession.mode ?? ChatServiceMode.COURSE;
        const entityId = newIrisSession.entityId ?? this.extractEntityIdFromIdentifier();
        const newIrisSessionDTO: IrisSessionDTO = {
            id: newIrisSession.id,
            creationDate: newIrisSession.creationDate,
            chatMode,
            entityId,
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

    private isLatestSessionIncludedInHistory(latestSession: IrisSessionDTO, currentSessions: IrisSessionDTO[] | undefined): boolean {
        const latestDisplayedSession: IrisSessionDTO | undefined = currentSessions?.[0];
        if (latestDisplayedSession === undefined) {
            return false;
        }

        if (latestDisplayedSession.id === latestSession.id) {
            return true;
        }

        return dayjs(latestSession.creationDate).isBefore(dayjs(latestDisplayedSession.creationDate));
    }

    private updateChatSessions(updatedSessions: IrisSessionDTO[], includeLatestSession: boolean): void {
        if (includeLatestSession && this.latestStartedSession) {
            updatedSessions.unshift(this.latestStartedSession);
        }
        this.chatSessions.next(updatedSessions);
    }

    private updateCurrentSessionContext(session: IrisSession | IrisSessionDTO): void {
        const chatMode = 'chatMode' in session && session.chatMode !== undefined ? session.chatMode : (session as IrisSession).mode;
        if (chatMode !== undefined) {
            this.currentChatModeSubject.next(chatMode);
        }
        const entityId = session.entityId ?? this.extractEntityIdFromIdentifier();
        if (entityId !== undefined) {
            this.currentRelatedEntityIdSubject.next(entityId);
        }
    }

    private extractEntityIdFromIdentifier(): number | undefined {
        if (!this.sessionCreationIdentifier) return undefined;
        const parts = this.sessionCreationIdentifier.split('/');
        const id = parts.length >= 2 ? Number(parts[parts.length - 1]) : undefined;
        return id && !isNaN(id) ? id : undefined;
    }

    private parseLatestSuggestions(str?: string): void {
        if (!str) {
            this.suggestions.next([]);
            return;
        }

        const suggestions = JSON.parse(str);
        this.suggestions.next(suggestions);
    }

    private handleWebsocketMessage(payload: IrisChatWebsocketDTO): void {
        if (payload.rateLimitInfo) {
            this.currentRatelimitInfoSubject.next(payload.rateLimitInfo);
        }
        if (payload.sessionTitle && this.sessionId) {
            if (this.latestStartedSession?.id === this.sessionId) {
                this.latestStartedSession = { ...this.latestStartedSession, title: payload.sessionTitle };
            }

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

    private mergeCitationInfo(existing: IrisCitationMetaDTO[], incoming: IrisCitationMetaDTO[]): IrisCitationMetaDTO[] {
        const merged = new Map<number, IrisCitationMetaDTO>();
        existing.forEach((citation) => merged.set(citation.entityId, citation));
        incoming.forEach((citation) => merged.set(citation.entityId, citation));
        return Array.from(merged.values());
    }

    private mapMessageDTO(dto: IrisMessageResponseDTO): IrisMessage {
        return Object.assign({}, dto, {
            sentAt: dto.sentAt ? dayjs(dto.sentAt) : undefined,
        }) as IrisMessage;
    }

    private filterStages(stages: IrisStageDTO[]): IrisStageDTO[] {
        return stages.filter((stage) => !stage.internal);
    }

    private handleSendHttpError(error: HttpErrorResponse): void {
        if (error.status === 403) {
            this.error.next(IrisErrorMessageKey.IRIS_DISABLED);
        } else if (error.status === 429) {
            this.error.next(IrisErrorMessageKey.RATE_LIMIT_EXCEEDED);
        } else {
            this.error.next(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
        }
    }

    private getCurrentSessionOrCreate(): Observable<IrisExerciseChatSession> {
        if (!this.sessionCreationIdentifier) {
            throw new Error('Session creation identifier not set');
        }

        return this.irisChatHttpService.getCurrentSessionOrCreateIfNotExists(this.sessionCreationIdentifier).pipe(
            map((response: HttpResponse<IrisExerciseChatSession>) => {
                if (response.body) {
                    return response.body;
                }
                throw new Error(IrisErrorMessageKey.SESSION_LOAD_FAILED);
            }),
            catchError(() => throwError(() => new Error(IrisErrorMessageKey.SESSION_LOAD_FAILED))),
        );
    }

    private createNewSession(): Observable<IrisExerciseChatSession> {
        if (!this.sessionCreationIdentifier) {
            throw new Error('Session creation identifier not set');
        }
        return this.irisChatHttpService.createSession(this.sessionCreationIdentifier).pipe(
            map((response: HttpResponse<IrisExerciseChatSession>) => {
                if (response.body) {
                    return response.body;
                }
                throw new Error(IrisErrorMessageKey.SESSION_CREATION_FAILED);
            }),
            catchError(() => throwError(() => new Error(IrisErrorMessageKey.SESSION_CREATION_FAILED))),
        );
    }

    private loadChatSessions(): void {
        const courseId = this.courseIdInternal();
        const latestStartedSession = this.latestStartedSession;
        if (courseId) {
            this.chatSessionSubscription?.unsubscribe();
            this.chatSessionSubscription = this.irisChatHttpService
                .getChatSessions(courseId)
                .pipe(takeUntil(this.contextSwitch$), takeUntilDestroyed(this.destroyRef))
                .subscribe((sessions: IrisSessionDTO[]) => {
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
                    sessionCreationIdentifier: this.sessionCreationIdentifier,
                },
                tags: { category: 'Iris' },
            });
            this.chatSessions.next([]);
        }
    }

    private checkHeartbeat(): void {
        const courseId = this.courseIdInternal();
        if (this.wsDisconnected || !courseId) {
            return;
        }
        // Avoid `firstValueFrom().then(...)` here — a Promise resolution can't be cancelled,
        // so an in-flight request resolving after host destroy would still mutate the
        // (now-detached) controller. Pipe through takeUntilDestroyed so the response is dropped.
        this.getIrisStatus(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((response: HttpResponse<IrisStatusDTO>) => {
                if (response.body) {
                    this.isActive = Boolean(response.body.active);
                    if (response.body.rateLimitInfo) {
                        this.currentRatelimitInfoSubject.next(response.body.rateLimitInfo);
                    }
                } else {
                    this.isActive = false;
                }
                this.activeSubject.next(this.isActive);
            });
    }

    private getIrisStatus(courseId: number): Response<IrisStatusDTO> {
        return this.httpClient.get<IrisStatusDTO>(`api/iris/courses/${courseId}/status`, { observe: 'response' });
    }
}
