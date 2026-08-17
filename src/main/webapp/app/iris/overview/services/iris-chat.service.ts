import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { IrisAssistantMessage, IrisMessage, IrisSender, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { BehaviorSubject, Observable, Subject, Subscription, catchError, map, of, tap, throwError } from 'rxjs';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
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
import { IrisMessageContextDTO } from 'app/iris/shared/entities/iris-message-context-dto.model';
import { IrisCommand } from 'app/iris/shared/entities/iris-command.model';
import { IrisPointOut, parsePointOut } from 'app/iris/shared/entities/iris-point-out.model';
import { randomInt } from 'app/foundation/util/utils';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { ChatServiceMode, SessionContext, sameSessionContext } from 'app/iris/shared/entities/iris-session-context.model';
import { IrisChatContextService } from 'app/iris/overview/services/iris-chat-context.service';
import { parseJson } from 'app/foundation/util/json.util';
import { toSignal } from '@angular/core/rxjs-interop';
import { IrisActivityItem, IrisRunState, IrisStatusError } from 'app/iris/shared/entities/iris-activity.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';
export type { SessionContext } from 'app/iris/shared/entities/iris-session-context.model';

export interface IrisLiveAssistantDraft {
    runId: string;
    text: string;
}

export interface IrisRunInfo {
    runId?: string;
    state: IrisRunState;
    error?: IrisStatusError;
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
    private readonly contextService = inject(IrisChatContextService);

    private modeRequiresLLMAcceptance = new Map<ChatServiceMode, boolean>([
        [ChatServiceMode.TEXT_EXERCISE, true],
        [ChatServiceMode.PROGRAMMING_EXERCISE, true],
        [ChatServiceMode.COURSE, true],
        [ChatServiceMode.LECTURE, true],
        [ChatServiceMode.TUTOR_SUGGESTION, false],
    ]);

    private currentSessionIdSubject = new BehaviorSubject<number | undefined>(undefined);
    private currentSessionId$ = this.currentSessionIdSubject.asObservable();

    readonly committedContext = this.contextService.committed;
    readonly displayContext = this.contextService.display;

    public get sessionId(): number | undefined {
        return this.currentSessionIdSubject.value;
    }

    public set sessionId(id: number | undefined) {
        this.currentSessionIdSubject.next(id);
    }

    messages: BehaviorSubject<IrisMessage[]> = new BehaviorSubject<IrisMessage[]>([]);
    newIrisMessage: BehaviorSubject<IrisMessage | undefined> = new BehaviorSubject<IrisMessage | undefined>(undefined);
    numNewMessages: BehaviorSubject<number> = new BehaviorSubject(0);
    runInfo: BehaviorSubject<IrisRunInfo | undefined> = new BehaviorSubject<IrisRunInfo | undefined>(undefined);
    activities: BehaviorSubject<IrisActivityItem[]> = new BehaviorSubject<IrisActivityItem[]>([]);
    suggestions: BehaviorSubject<string[]> = new BehaviorSubject<string[]>([]);
    citationInfo: BehaviorSubject<IrisCitationMetaDTO[]> = new BehaviorSubject<IrisCitationMetaDTO[]>([]);
    liveAssistantDraft: BehaviorSubject<IrisLiveAssistantDraft | undefined> = new BehaviorSubject<IrisLiveAssistantDraft | undefined>(undefined);
    error: BehaviorSubject<IrisErrorMessageKey | undefined> = new BehaviorSubject<IrisErrorMessageKey | undefined>(undefined);
    chatSessions: BehaviorSubject<IrisSessionDTO[]> = new BehaviorSubject<IrisSessionDTO[]>([]);

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
    private websocketCommandSubscription?: Subscription;
    private authenticationStateSubscription: Subscription;

    /**
     * Incremented every time {@link resetState} runs. HTTP/observable side effects that may complete
     * after a reset capture the generation at call time and short-circuit if it no longer matches,
     * preventing them from repopulating cleared state with the previous user's data.
     */
    private stateGeneration = 0;

    private lastSeenPartialSeqByRunId = new Map<string, number>();
    private lastActivitySeqByRunId = new Map<string, number>();
    private knownRunIds = new Set<string>();
    private terminalRunStateByRunId = new Map<string, IrisRunState>();
    private currentRunId?: string;

    private finalizedRunIds = new Set<string>();
    private pendingRunGeneration = signal(false);
    private readonly runInfoSignal = toSignal(this.runInfo.asObservable(), { initialValue: undefined });
    private readonly answeredRunIds = signal<ReadonlySet<string>>(new Set<string>());
    readonly awaitingAnswer = computed(() => {
        if (this.pendingRunGeneration()) {
            return true;
        }
        const info = this.runInfoSignal();
        if (info?.state !== IrisRunState.RUNNING) {
            return false;
        }
        return !info.runId || !this.answeredRunIds().has(info.runId);
    });

    private shouldReopenChatSubject = new BehaviorSubject<boolean>(false);
    public shouldReopenChat$ = this.shouldReopenChatSubject.asObservable();

    // Emits when Iris points the student to a position in the combined view, either pushed by the server
    // mid-pipeline (then it carries a correlationId to acknowledge) or raised by a marker click in the chat
    // history. The lecture combined view subscribes and navigates.
    private pointOutSubject = new Subject<IrisPointOut>();
    public pointOut$ = this.pointOutSubject.asObservable();

    private llmOptedOutSubject = new Subject<void>();
    public llmOptedOut$ = this.llmOptedOutSubject.asObservable();

    hasJustAcceptedLLMUsage = false;

    /**
     * The AI-experience decision as last confirmed by the server, captured before an optimistic update so a failed
     * update can be rolled back. Set while a consent request is in flight and cleared once it settles, so a rapid
     * second choice does not overwrite it with the first choice's unpersisted value.
     */
    private lastConfirmedDecision?: { decision?: LLMSelectionDecision; timestamp?: dayjs.Dayjs };

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
        this.websocketCommandSubscription?.unsubscribe();
        this.websocketCommandSubscription = undefined;
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
        this.contextService.reset();
        this.messages.next([]);
        this.resetRunTracking();
        this.suggestions.next([]);
        this.citationInfo.next([]);
        this.resetLiveAssistantDraftTracking();
        this.numNewMessages.next(0);
        this.newIrisMessage.next(undefined);
        this.error.next(undefined);
        this.chatSessions.next([]);
        this.shouldReopenChatSubject.next(false);
        // Plain fields.
        this.latestStartedSession = undefined;
        this.hasJustAcceptedLLMUsage = false;
        // The snapshot belongs to the user whose consent request was just cancelled above. Keeping it would
        // make the next user's rollback restore the previous user's decision into their identity cache.
        this.lastConfirmedDecision = undefined;
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
        this.websocketCommandSubscription?.unsubscribe();
        this.authenticationStateSubscription.unsubscribe();
    }

    protected start() {
        const sessionContext = this.contextService.page();
        const requiresAcceptance = sessionContext ? this.modeRequiresLLMAcceptance.get(sessionContext.mode) : true;
        if (
            requiresAcceptance === false ||
            this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.LOCAL_AI ||
            this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.CLOUD_AI ||
            this.hasJustAcceptedLLMUsage
        ) {
            this.sessionLoadingSubscription?.unsubscribe();
            this.sessionLoadingSubscription = this.getCurrentSessionOrCreate().subscribe(cloneWith(this.handleNewSession(), { complete: () => this.loadChatSessions() }));
        }
    }

    /**
     * Sends a message to the server and returns the created message.
     *
     * If the user has selected a different context via the dropdown since the last send
     * ({@link IrisChatContextService.pending}), it is included in the request body so the server applies the
     * context switch atomically (CTXSWAP marker first, then the user message) in one round trip.
     *
     * @param message to be created
     * @param uncommittedFiles optional map of uncommitted file changes (path to content)
     * @param context optional list of context objects providing information about what the user is viewing
     */
    public sendMessage(message: string, uncommittedFiles: { [path: string]: string } = {}, context?: IrisMessageContextDTO[]): Observable<undefined> {
        if (!this.sessionId) {
            // Surface this instead of failing silently: onSend() clears the textarea regardless of the
            // outcome, so a swallowed error drops the user's message without telling them anything.
            this.error.next(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
            return throwError(() => new Error('Not initialized'));
        }

        // Trim messages (Spaces, newlines)
        message = message.trim();

        const pendingContext = this.contextService.pending();
        const pendingContextDTO = pendingContext ? { mode: pendingContext.mode, entityId: pendingContext.entityId } : undefined;
        const requestSessionId = this.sessionId;
        const requestDTO: IrisMessageRequestDTO = {
            content: [IrisMessageContentDTO.text(message)],
            messageDifferentiator: randomInt(),
            uncommittedFiles,
            pendingContext: pendingContextDTO,
            context,
            // Travels with the message so a command Iris issues while answering comes back addressed to this tab
            // rather than to every tab the user has the session open in.
            clientId: this.irisWebsocketService.clientId,
        };

        const generation = this.stateGeneration;
        this.openPendingRunGeneration();
        return this.irisChatHttpService.createMessage(requestSessionId, requestDTO).pipe(
            tap((response: HttpResponse<IrisMessageResponseDTO>) => {
                if (this.stateGeneration !== generation || this.sessionId !== requestSessionId) return;
                if (pendingContext) {
                    this.contextService.commitSentContext(pendingContext);
                    // Reflect the committed context in the sidebar entry immediately — without this,
                    // the related-entity icon/tooltip would stay stale until the next loadChatSessions().
                    const updatedSessions = this.chatSessions
                        .getValue()
                        .map((session) =>
                            session.id === requestSessionId
                                ? cloneWith(session, { mode: pendingContext.mode, entityId: pendingContext.entityId, entityName: pendingContext.entityName ?? session.entityName })
                                : session,
                        );
                    this.chatSessions.next(updatedSessions);
                }
                this.suggestions.next([]);
                this.replaceOrAddMessage(this.mapMessageDTO(response.body!));
            }),
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                if (this.stateGeneration !== generation || this.sessionId !== requestSessionId) return of(undefined);
                this.closePendingRunGeneration();
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

    private replaceOrAddMessage(message: IrisMessage, announceNewAssistantMessage = true) {
        const messageWasReplaced = this.replaceMessage(message);
        if (!messageWasReplaced) {
            if (message.sender === IrisSender.LLM && announceNewAssistantMessage) {
                this.newIrisMessage.next(message);
            }
            // Keep the list ordered by send time: a message can arrive over the websocket and via the
            // sendMessage HTTP response, so arrival order is racy. sentAt is a dayjs object for runtime
            // messages but a raw ISO string for session-loaded ones, hence dayjs(); ties break by id.
            const messages = [...this.messages.getValue(), message];
            messages.sort((a, b) => {
                const timeDifference = dayjs(a.sentAt).valueOf() - dayjs(b.sentAt).valueOf();
                if (timeDifference !== 0) {
                    return timeDifference;
                }
                return (a.id ?? 0) - (b.id ?? 0);
            });
            this.messages.next(messages);
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
        this.openPendingRunGeneration();
        return this.irisChatHttpService.resendMessage(this.sessionId, message).pipe(
            map((r: HttpResponse<IrisMessageResponseDTO>) => this.mapMessageDTO(r.body!)),
            tap((m) => {
                if (this.stateGeneration !== generation) return;
                this.replaceMessage(m);
            }),
            map(() => undefined),
            catchError((error: HttpErrorResponse) => {
                if (this.stateGeneration !== generation) return of();
                this.closePendingRunGeneration();
                this.handleSendHttpError(error);
                return of();
            }),
        );
    }

    private handleSendHttpError(error: HttpErrorResponse): void {
        if (error.status === 403) {
            this.error.next(IrisErrorMessageKey.IRIS_DISABLED);
        } else if (error.status === 429) {
            const map = new Map<string, number | undefined>();
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
        return this.irisChatHttpService.rateMessage(this.sessionId, message.id, !!helpful).pipe(
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
        // Publish the decision to the cached user identity right away, before the request resolves: the chatbot
        // gates the "Choose Your AI Experience" modal on `userIdentity().selectedLLMUsage`, so a chat that is
        // (re-)opened while the request is still in flight would otherwise read the stale value and ask the user
        // to choose again. Reverted in the error handlers below if persisting the decision fails.
        //
        // The snapshot is only taken when nothing is in flight. A second choice made while the first request is
        // still running would otherwise capture that first, unpersisted decision as its "previous" value and
        // roll back to something the server may never have stored.
        const identity = this.accountService.userIdentity();
        this.lastConfirmedDecision ??= { decision: identity?.selectedLLMUsage, timestamp: identity?.selectedLLMUsageTimestamp };
        const snapshot = this.lastConfirmedDecision;
        const revertDecision = () => {
            this.lastConfirmedDecision = undefined;
            this.accountService.restoreUserLLMSelectionDecision(snapshot.decision, snapshot.timestamp);
        };
        this.accountService.setUserLLMSelectionDecision(accepted);

        if (accepted === LLMSelectionDecision.NO_AI) {
            this.hasJustAcceptedLLMUsage = false;
            this.acceptSubscription?.unsubscribe();
            this.acceptSubscription = this.userService.updateLLMSelectionDecision(accepted).subscribe({
                next: () => {
                    this.lastConfirmedDecision = undefined;
                    this.llmOptedOutSubject.next();
                    this.close();
                },
                error: () => {
                    revertDecision();
                    this.error.next(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE);
                    this.close();
                },
            });
            return;
        }
        this.acceptSubscription?.unsubscribe();
        this.acceptSubscription = this.userService.updateLLMSelectionDecision(accepted).subscribe({
            next: () => {
                this.lastConfirmedDecision = undefined;
                this.hasJustAcceptedLLMUsage = true;
                // Only start the session that could not be created before the user opted in (the server rejects
                // session creation without consent). If one is already established — the chat was reopened right
                // after the choice and start() succeeded on its own — leave it alone: closing it here would drop
                // the websocket subscription and discard the response to a message the user already sent.
                if (!this.sessionId) {
                    this.closeAndStart();
                }
            },
            error: () => {
                revertDecision();
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

        const newIrisSessionDTO: IrisSessionDTO = {
            id: newIrisSession.id,
            creationDate: newIrisSession.creationDate,
            mode: newIrisSession.mode,
            entityId: newIrisSession.entityId,
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

    private handleNewSession() {
        return {
            next: (newIrisSession: IrisSession) => {
                this.addLatestEmptySessionToChatSessions(newIrisSession);
                const serverCtx: SessionContext | undefined = newIrisSession.mode ? { mode: newIrisSession.mode, entityId: newIrisSession.entityId } : undefined;
                this.contextService.adoptServerContext(serverCtx);

                this.sessionId = newIrisSession.id;
                this.citationInfo.next(newIrisSession.citationInfo || []);
                this.messages.next(newIrisSession.messages || []);
                this.parseLatestSuggestions(newIrisSession.latestSuggestions);
                this.resetLiveAssistantDraftTracking();
                this.resetRunTracking();
                // Flip the gate before subscribing to the websocket: the load itself has
                // succeeded, so consumers waiting on "messages have settled" are unblocked
                // even if the websocket layer throws synchronously (e.g. mocked-out in tests).
                this.initialLoadCompleteSubject.next(true);
                this.websocketSessionSubscription?.unsubscribe();
                this.websocketSessionSubscription = this.irisWebsocketService.subscribeToSession(this.sessionId).subscribe((message) => this.handleWebsocketMessage(message));
                this.websocketCommandSubscription?.unsubscribe();
                this.websocketCommandSubscription = this.irisWebsocketService.subscribeToSessionCommands(this.sessionId).subscribe((command) => this.handleCommand(command));
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

        const suggestions = parseJson<string[]>(str);
        this.suggestions.next(suggestions);
    }

    private handleWebsocketMessage(payload: IrisChatWebsocketDTO) {
        if (payload.rateLimitInfo) {
            this.irisStatusService.handleRateLimitInfo(payload.rateLimitInfo);
        }
        if (!this.shouldApplyRunScopedPayload(payload)) {
            return;
        }
        if (payload.sessionTitle && this.sessionId) {
            if (this.latestStartedSession?.id === this.sessionId) {
                this.latestStartedSession = cloneWith(this.latestStartedSession, { title: payload.sessionTitle });
            }

            // Update the observable list immutably so OnPush change detection picks up the new title immediately.
            const updatedSessions = this.chatSessions.getValue().map((session) => (session.id === this.sessionId ? cloneWith(session, { title: payload.sessionTitle }) : session));
            this.chatSessions.next(updatedSessions);
        }
        if (payload.citationInfo?.length) {
            const merged = this.mergeCitationInfo(this.citationInfo.getValue(), payload.citationInfo);
            this.citationInfo.next(merged);
        }
        this.applyRunState(payload);
        switch (payload.type) {
            case IrisChatWebsocketPayloadType.MESSAGE:
                this.handleMessageWebsocketPayload(payload);
                break;
            case IrisChatWebsocketPayloadType.PARTIAL:
                this.handlePartialWebsocketMessage(payload);
                break;
            case IrisChatWebsocketPayloadType.STATUS:
                this.applyActivitySnapshot(payload);
                if (payload.suggestions) {
                    this.suggestions.next(payload.suggestions);
                }
                break;
        }
    }

    private shouldApplyRunScopedPayload(payload: IrisChatWebsocketDTO): boolean {
        const runId = payload.runId;
        if (!runId) {
            return true;
        }

        const isKnownRun = this.knownRunIds.has(runId);
        if (this.pendingRunGeneration() && isKnownRun) {
            return false;
        }
        if (!isKnownRun) {
            this.knownRunIds.add(runId);
            this.currentRunId = runId;
            this.closePendingRunGeneration();
        }
        if (this.currentRunId && runId !== this.currentRunId) {
            return false;
        }

        const terminalState = this.terminalRunStateByRunId.get(runId);
        if (terminalState && payload.runState && payload.runState !== terminalState) {
            return false;
        }
        return true;
    }

    private applyRunState(payload: IrisChatWebsocketDTO): void {
        if (!payload.runState) {
            return;
        }
        // Run-state frames from the shorter sendMessage(...) overloads omit runId;
        // keep the last known run id so awaitingAnswer() does not get stuck true.
        const runId = payload.runId ?? this.runInfo.getValue()?.runId;
        const nextRunInfo: IrisRunInfo = {
            runId,
            state: payload.runState,
            error: payload.error,
        };
        this.runInfo.next(nextRunInfo);
        if (runId && this.isTerminalRunState(payload.runState)) {
            this.terminalRunStateByRunId.set(runId, payload.runState);
        }
        if (payload.runState === IrisRunState.FAILED) {
            this.closePendingRunGeneration();
        }
    }

    private handleMessageWebsocketPayload(payload: IrisChatWebsocketDTO): void {
        const isIntermediateMessage = this.isIntermediateMessagePayload(payload);
        if (payload.runId && !isIntermediateMessage) {
            this.finalizedRunIds.add(payload.runId);
            this.lastSeenPartialSeqByRunId.delete(payload.runId);
        }
        // The backend can resend an already-persisted assistant message (same id) to
        // attach createdMemories; only fire the unread-badge side effects for a new id.
        const isNewMessage = payload.message?.id === undefined || !this.messages.getValue().some((existing) => existing.id === payload.message!.id);
        if (payload.message?.sender === IrisSender.LLM) {
            if (!isIntermediateMessage && isNewMessage) {
                this.markAnswerArrived(payload.runId);
                this.numNewMessages.next(this.numNewMessages.getValue() + 1);
            }
            if (payload.runId && !isIntermediateMessage) {
                this.activities.next([]);
            }
        }
        if (payload.message?.id) {
            this.replaceOrAddMessage(this.mapMessageDTO(payload.message), !isIntermediateMessage);
        }
        // Clear the draft only AFTER the final message was applied: removing the
        // draft first shrinks the scroll content for one frame, which clamps
        // scrollTop and visually snaps the viewport to the top of the answer.
        this.liveAssistantDraft.next(undefined);
    }

    private isIntermediateMessagePayload(payload: IrisChatWebsocketDTO): boolean {
        return payload.final === false || payload.message?.final === false;
    }

    private handlePartialWebsocketMessage(payload: IrisChatWebsocketDTO): void {
        if (!payload.runId || payload.partialResult === undefined || payload.partialSeq === undefined) {
            return;
        }
        if (this.finalizedRunIds.has(payload.runId)) {
            return;
        }
        const lastSeenSeq = this.lastSeenPartialSeqByRunId.get(payload.runId);
        if (lastSeenSeq !== undefined && payload.partialSeq <= lastSeenSeq) {
            return;
        }
        this.lastSeenPartialSeqByRunId.set(payload.runId, payload.partialSeq);
        this.liveAssistantDraft.next({ runId: payload.runId, text: payload.partialResult });
    }

    private applyActivitySnapshot(payload: IrisChatWebsocketDTO): void {
        if (!payload.runId || payload.activitySeq === undefined || !payload.activities) {
            return;
        }
        if (this.finalizedRunIds.has(payload.runId)) {
            return;
        }
        const lastSeenSeq = this.lastActivitySeqByRunId.get(payload.runId);
        if (lastSeenSeq !== undefined && payload.activitySeq <= lastSeenSeq) {
            return;
        }
        this.lastActivitySeqByRunId.set(payload.runId, payload.activitySeq);
        this.activities.next(payload.activities);
    }

    private resetLiveAssistantDraftTracking(): void {
        this.liveAssistantDraft.next(undefined);
        this.lastSeenPartialSeqByRunId.clear();
        this.finalizedRunIds.clear();
    }

    private openPendingRunGeneration(): void {
        this.runInfo.next(undefined);
        this.activities.next([]);
        this.pendingRunGeneration.set(true);
    }

    private closePendingRunGeneration(): void {
        this.pendingRunGeneration.set(false);
    }

    private markAnswerArrived(runId?: string): void {
        this.closePendingRunGeneration();
        if (!runId) {
            return;
        }
        this.answeredRunIds.update((runIds) => new Set(runIds).add(runId));
    }

    private resetRunTracking(): void {
        this.runInfo.next(undefined);
        this.activities.next([]);
        this.lastActivitySeqByRunId.clear();
        this.knownRunIds.clear();
        this.terminalRunStateByRunId.clear();
        this.currentRunId = undefined;
        this.pendingRunGeneration.set(false);
        this.answeredRunIds.set(new Set<string>());
    }

    private mapMessageDTO(dto: IrisMessageResponseDTO): IrisMessage {
        return cloneWith(dto, {
            sentAt: dto.sentAt ? dayjs(dto.sentAt) : undefined,
        }) as IrisMessage;
    }

    private isTerminalRunState(runState: IrisRunState): boolean {
        return runState === IrisRunState.FINISHED || runState === IrisRunState.FAILED;
    }

    protected close(): void {
        if (this.sessionId) {
            this.irisWebsocketService.unsubscribeFromSession(this.sessionId);
            this.websocketSessionSubscription?.unsubscribe();
            this.websocketSessionSubscription = undefined;
            this.websocketCommandSubscription?.unsubscribe();
            this.websocketCommandSubscription = undefined;
            this.sessionId = undefined;
            this.messages.next([]);
            this.resetRunTracking();
            this.suggestions.next([]);
            this.citationInfo.next([]);
            this.resetLiveAssistantDraftTracking();
            this.numNewMessages.next(0);
            this.newIrisMessage.next(undefined);
            this.initialLoadCompleteSubject.next(false);
        }
        this.error.next(undefined);
    }

    /**
     * Retrieves the current session or creates a new one if it doesn't exist.
     */
    private getCurrentSessionOrCreate(): Observable<IrisSession> {
        const pageContext = this.contextService.page();
        if (!pageContext) {
            throw new Error('Page context not set');
        }

        return this.irisChatHttpService.getCurrentSessionOrCreateIfNotExists(pageContext.mode, pageContext.entityId).pipe(
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

    private createCourseSession(): Observable<IrisSession> {
        const courseId = this.getCourseId();
        if (!courseId) {
            throw new Error('Course ID not set');
        }

        return this.irisChatHttpService.createCourseSession(courseId).pipe(
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
                },
                tags: {
                    category: 'Iris',
                },
            });
            this.chatSessions.next([]);
        }
    }

    /**
     * Tutor-suggestion entry point (e.g. from a communication thread). The TUTOR_SUGGESTION mode
     * bypasses LLM-consent gating in {@link start} via {@link modeRequiresLLMAcceptance}.
     */
    public openTutorSuggestionChat(postId: number): void {
        const ctx: SessionContext = { mode: ChatServiceMode.TUTOR_SUGGESTION, entityId: postId };
        if (sameSessionContext(ctx, this.contextService.page())) return;
        this.contextService.setPageContext(ctx);
        this.closeAndStart();
    }

    /**
     * Page entry point for course / lecture / exercise mounts. Stages the page context and (re)opens its
     * session via {@link start}; no-op when the page context is unchanged. The server resolves the session:
     * an existing lecture/exercise chat with history is resumed, otherwise it falls back to the course session
     * and the page context is staged as pending (see {@link IrisChatContextService.adoptServerContext}).
     */
    public openChat(mode: ChatServiceMode, entityId: number): void {
        const ctx: SessionContext = { mode: mode, entityId: entityId };
        if (sameSessionContext(ctx, this.contextService.page())) return;
        this.contextService.setPageContext(ctx);
        this.closeAndStart();
    }

    /**
     * Stages a context override; the server only sees it on the next {@link sendMessage},
     * which commits it via a CTXSWAP marker. Reverting to the committed context clears the override.
     * Safe to call before {@link sessionId} is set (e.g. during lecture/exercise auto-preselect).
     */
    public stagePendingContext(mode: ChatServiceMode, entityId: number, entityName?: string): void {
        this.contextService.stagePending({ mode, entityId, entityName });
    }

    /**
     * Closes the active session and opens a fresh COURSE session for the current course.
     * No-op if the current session is already empty (there is nothing to start fresh from).
     * <p>
     * On lecture / exercise pages the page context is re-applied asynchronously by {@link handleNewSession}
     * once the new session loads (see {@link IrisChatContextService.adoptServerContext}), so the caller does
     * not need to stage it. The chip does not blink in the meantime because {@link close} leaves the context
     * signals untouched.
     */
    public startFreshChat(): void {
        const courseId = this.getCourseId();
        const isFreshCourseSession = this.messages.getValue().length === 0;
        if (!isFreshCourseSession && courseId) {
            this.close();
            this.sessionLoadingSubscription?.unsubscribe();
            this.sessionLoadingSubscription = this.createCourseSession().subscribe(cloneWith(this.handleNewSession(), { complete: () => this.loadChatSessions() }));
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
            this.chatSessionByIdSubscription = this.irisChatHttpService.getChatSessionById(courseId, session.id).subscribe((session) => {
                this.handleNewSession().next(session);
            });
        } else {
            captureException(new Error('Could not switch session, courseId is not set.'), {
                extra: {
                    currentUrl: this.router.url,
                    userId: this.accountService.userIdentity()?.id,
                    sessionId: this.sessionId,
                    sessionContext: this.contextService.committed(),
                },
                tags: {
                    category: 'Iris',
                },
            });
        }
    }

    private closeAndStart() {
        this.close();
        this.start();
    }

    public currentSessionId(): Observable<number | undefined> {
        return this.currentSessionId$;
    }

    public currentMessages(): Observable<IrisMessage[]> {
        return this.messages.asObservable();
    }

    public currentRunInfo(): Observable<IrisRunInfo | undefined> {
        return this.runInfo.asObservable();
    }

    public currentActivities(): Observable<IrisActivityItem[]> {
        return this.activities.asObservable();
    }

    public currentCitationInfo(): Observable<IrisCitationMetaDTO[]> {
        return this.citationInfo.asObservable();
    }

    public currentLiveAssistantDraft(): Observable<IrisLiveAssistantDraft | undefined> {
        return this.liveAssistantDraft.asObservable();
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

    /**
     * Triggers navigation to a point-out marker's position, (re)opening the combined view if needed.
     * Used when the student clicks a COMMAND marker in the chat history.
     *
     * The lecture unit that carries out a point-out only listens while it is on screen. Chat history, however, opens a
     * lecture session from anywhere — the course Iris page above all — and there the marker would emit into the void
     * and the click would do nothing at all. So when the marker's lecture is not the one the route is showing, the
     * target is handed to that lecture's deep link instead, which reaches the same position through the route and
     * applies it as the page builds. Only a marker whose lecture is already open is delivered in place, where the
     * combined view can move without a reload.
     *
     * The lecture comes off the marker rather than out of the session's context: a conversation can be switched to
     * another lecture after a point-out was made, and an older marker still points where it pointed then.
     * @param pointOut the navigation target (the caller should set forceOpen to reopen a closed view)
     */
    public navigateToPointOut(pointOut: IrisPointOut): void {
        const courseId = this.getCourseId();
        const pageContext = this.contextService.page();
        const showsMarkersLecture = pageContext?.mode === ChatServiceMode.LECTURE && pageContext.entityId === pointOut.lectureId;
        if (pointOut.lectureId != undefined && courseId && !showsMarkersLecture) {
            // Same deep link the lecture citations use, so both ways of pointing at a position arrive the same way.
            // Unlike a citation it also asks for the combined view, which is where Iris did the pointing and where
            // the toggle and its explanation live — otherwise the same click would land in a different place
            // depending on which page the student happened to start from.
            const queryParams: Record<string, number | boolean> = { unit: pointOut.lectureUnitId, combined: true };
            if (pointOut.page != undefined) {
                queryParams.page = pointOut.page;
            }
            if (pointOut.timestamp != undefined) {
                queryParams.timestamp = pointOut.timestamp;
            }
            void this.router.navigate(['/courses', courseId, 'lectures', pointOut.lectureId], { queryParams });
            return;
        }
        this.pointOutSubject.next(pointOut);
    }

    /**
     * Carries out a command pushed by the server, dispatching on its type. Supporting a further type means adding a
     * case here. Anything else — including a command whose parameters do not hold up — is acknowledged as not applied
     * right away, so the waiting pipeline learns the outcome instead of running into its ack timeout.
     *
     * A command addressed to a different tab is still carried out here, but never acknowledged: answering for it would
     * let a bystanding tab report failure while the addressed one is still navigating.
     * @param command the command pushed by the server
     */
    private handleCommand(command: IrisCommand): void {
        // Delivery is per user, so this arrives in every tab with the session open. All of them carry the command out —
        // the student should find the same position in whichever tab they look at next — but only the tab the run was
        // started from answers for it. The others drop the correlation id and then navigate without saying anything.
        //
        // Unlike a marker click this never routes a tab elsewhere, even where the lecture unit is not on screen to
        // receive it: a click is the student asking to be taken somewhere, while this arrives on its own and would
        // pull them out of whatever they were doing. Such a tab therefore does nothing, and where it was also the one
        // answering, the pipeline is released by the server-side ack timeout.
        const answersForCommand = !command.targetClientId || command.targetClientId === this.irisWebsocketService.clientId;
        switch (command.type) {
            case 'pointOut': {
                const pointOut = parsePointOut(command.parameters);
                if (pointOut) {
                    if (answersForCommand) {
                        // The pipeline is waiting on this one; the combined view acknowledges once it has actually moved.
                        pointOut.correlationId = command.correlationId;
                    }
                    this.pointOutSubject.next(pointOut);
                    return;
                }
                break;
            }
        }
        if (answersForCommand && typeof command.correlationId === 'string') {
            this.sendCommandAck(command.correlationId, false);
        }
    }

    /**
     * Acknowledges a server command request, unblocking the Iris pipeline that is waiting on it.
     * @param correlationId the correlation id of the request being answered
     * @param applied       whether the command was carried out on the client
     */
    public sendCommandAck(correlationId: string, applied: boolean): void {
        this.irisWebsocketService.sendCommandAck({ correlationId, applied });
    }
}
