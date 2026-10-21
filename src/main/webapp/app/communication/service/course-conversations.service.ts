import { Injectable, OnDestroy, inject } from '@angular/core';
import { EMPTY, Observable, ReplaySubject, Subject, Subscription, catchError, finalize, map, of, switchMap, tap, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { ConversationWebsocketDTO } from 'app/communication/shared/entities/conversation/conversation-websocket-dto.model';
import { CommunicationCrudAction, CommunicationWebsocketChannelPrefix, RouteComponents } from 'app/communication/communication.util';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { onError } from 'app/foundation/util/global.utils';
import { Course } from 'app/course/shared/entities/course.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import dayjs from 'dayjs/esm';
import { NavigationEnd, Params, Router } from '@angular/router';
import { PostBroadcastDTO } from 'app/communication/shared/entities/post-broadcast-dto.model';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';

/** The automatic reads covered by a messages request and the manual-unread baseline it started from. */
export interface ConversationReadRequest {
    readonly coveredRequests: number;
    readonly unreadRevision: number;
}

/**
 * Holds the conversations the current user is a member of in one course, the active conversation and the unread and
 * code of conduct state, and keeps them current over the conversation membership websocket topic.
 * <p>
 * NOTE: NOT INJECTED IN THE ROOT MODULE. Components provide their own instance, mainly the course overview and course
 * management pages, where it lives as long as the course is open.
 */
@Injectable()
export class CourseConversationsService implements OnDestroy {
    private groupChatService = inject(GroupChatService);
    private oneToOneChatService = inject(OneToOneChatService);
    private channelService = inject(ChannelService);
    private conversationService = inject(ConversationService);
    private websocketService = inject(WebsocketService);
    private accountService = inject(AccountService);
    private alertService = inject(AlertService);
    private router = inject(Router);

    // Stores the conversation of the course where the current user is a member
    private conversationsOfUser: ConversationDTO[] = [];
    /**
     * Whether the conversations of the user have been loaded at least once. Until then an empty cache means "not known
     * yet" rather than "the user is in no conversation", and the two lead to opposite behaviour.
     */
    private conversationsLoaded = false;
    /**
     * A conversation requested before the cache held anything, to be activated once it arrives.
     * <p>
     * Opening the page with `?conversationId=` asks for a conversation while the request that fetches them is still in
     * flight, because the route emits its query parameters immediately. Dropping that request leaves the page on an
     * empty view that nothing ever fills, since it is the only request that will be made.
     */
    private requestedConversationId: number | undefined = undefined;
    // Incremented by every selection so that the answer to an older refresh cannot replace a newer selection
    private selectionVersion = 0;
    _conversationsOfUser$: ReplaySubject<ConversationDTO[]> = new ReplaySubject<ConversationDTO[]>(1);
    // Stores the currently selected conversation
    private activeConversation: ConversationDTO | undefined = undefined;
    _activeConversation$: ReplaySubject<ConversationDTO | undefined> = new ReplaySubject<ConversationDTO | undefined>(1);
    private isCodeOfConductAccepted = false;
    _isCodeOfConductAccepted$: ReplaySubject<boolean> = new ReplaySubject<boolean>(1);
    private isCodeOfConductPresented = false;
    _isCodeOfConductPresented$: ReplaySubject<boolean> = new ReplaySubject<boolean>(1);
    private hasUnreadMessages = false;
    _hasUnreadMessages$: Subject<boolean> = new ReplaySubject<boolean>(1);
    private isMarkedAsUnread = false;
    private isConversationViewOpen = false;
    // Per conversation, the number of the last read request sent, and the numbers of the newest one that succeeded and that failed. Read requests of
    // the same conversation can complete out of order, and only the newest outcome describes the count the server holds.
    private readRequests = new Map<number, { sent: number; succeeded: number; failed: number; unreadRevision: number; failedContributions: Set<number> }>();
    // Stores the course for which the service is set up -> should not change during the lifetime of the service
    private _course: Course | undefined = undefined;
    // Stores if the service is currently loading data
    private isLoading = false;
    _isLoading$: ReplaySubject<boolean> = new ReplaySubject<boolean>(1);

    private subscribedConversationMembershipTopic?: string;
    private activeConversationSubscription?: Subscription;
    private conversationMembershipSubscription?: Subscription;

    private userId!: number; // set in constructor from accountService.identity()
    private _courseId!: number; // set in setUpConversationService() before any read

    private _isServiceSetup$: ReplaySubject<boolean> = new ReplaySubject<boolean>(1);

    constructor() {
        void this.accountService.identity().then((user: User | undefined) => {
            if (!user) {
                return;
            }
            this.userId = user.id!;
            const conversationTopic = `/topic/user/${this.userId}/notifications/conversations`;
            this.activeConversationSubscription = this.websocketService.subscribe<PostBroadcastDTO>(conversationTopic).subscribe((postDTO: PostBroadcastDTO) => {
                if (postDTO.action === CommunicationCrudAction.CREATE && postDTO.post?.author?.id !== this.userId) {
                    this.handleNewMessage(postDTO.post?.conversation?.id, postDTO.post?.conversation?.lastMessageDate);
                }
            });
        });
    }

    ngOnDestroy(): void {
        this.cleanupSubscriptions();
    }

    private cleanupSubscriptions() {
        if (this.subscribedConversationMembershipTopic) {
            this.conversationMembershipSubscription?.unsubscribe();
            this.subscribedConversationMembershipTopic = undefined;
        }

        if (this.activeConversationSubscription) {
            this.activeConversationSubscription.unsubscribe();
            this.activeConversationSubscription = undefined;
        }
    }

    get conversationsOfUser$(): Observable<ConversationDTO[]> {
        return this._conversationsOfUser$.asObservable();
    }
    get activeConversation$(): Observable<ConversationDTO | undefined> {
        return this._activeConversation$.asObservable();
    }
    get isCodeOfConductAccepted$(): Observable<boolean> {
        return this._isCodeOfConductAccepted$.asObservable();
    }
    get isCodeOfConductPresented$(): Observable<boolean> {
        return this._isCodeOfConductPresented$.asObservable();
    }
    get hasUnreadMessages$(): Observable<boolean> {
        return this._hasUnreadMessages$.asObservable();
    }

    get isServiceSetup$(): Observable<boolean> {
        return this._isServiceSetup$.asObservable();
    }

    get course(): Course | undefined {
        return this._course;
    }

    get isLoading$(): Observable<boolean> {
        return this._isLoading$.asObservable();
    }

    public setActiveConversation(conversationIdentifier: ConversationDTO | number | undefined) {
        const selectionVersion = ++this.selectionVersion;
        let cachedConversation: ConversationDTO | undefined = undefined;
        let conversationId: number | undefined = undefined;
        if (conversationIdentifier) {
            const parameterJustId = typeof conversationIdentifier === 'number';
            conversationId = parameterJustId ? conversationIdentifier : conversationIdentifier.id;
            cachedConversation = this.conversationsOfUser.find((conversationInCache) => conversationInCache.id === conversationId);
        }
        if (!cachedConversation && conversationIdentifier) {
            if (!this.conversationsLoaded) {
                // Not a membership problem: the cache simply has nothing to look in yet. Remember what was asked for
                // and activate it when the list arrives. Warning about membership here would be wrong too, because
                // whether the user is a member is not known until the conversations are in.
                this.requestedConversationId = conversationId;
                return;
            }
            // The conversation can be missing because it was created after the conversations were loaded: nobody is told about a new
            // course-wide channel, although every user of the course may open it. Ask the server again before concluding that the user
            // is not a member. Otherwise following the notification about such a channel only shows the warning.
            this.forceRefresh().subscribe({
                complete: () => {
                    if (selectionVersion !== this.selectionVersion) {
                        // the user selected something else while the conversations were loading, which is the more recent intent
                        return;
                    }
                    if (this.conversationsOfUser.some((conversationInCache) => conversationInCache.id === conversationId)) {
                        this.setActiveConversation(conversationId);
                    } else {
                        this.alertService.addAlert({
                            type: AlertType.WARNING,
                            message: 'artemisApp.communication.channel.notAMember',
                        });
                    }
                },
                // the failed request has already been reported by forceRefresh
                error: () => {},
            });
            // Keep whatever is currently open instead of closing it. Replacing it with nothing empties the view, and the
            // active conversation subscriber reacts to that by removing the conversationId from the URL, so a reload can
            // no longer restore the conversation either. Clearing on purpose stays possible by passing undefined.
            return;
        }
        if (this.activeConversation?.id !== conversationId) {
            this.updateConversationAsRead();
        }
        this.requestedConversationId = undefined;
        this.activeConversation = cachedConversation;
        this._activeConversation$.next(this.activeConversation);
        this.isCodeOfConductPresented = false;
        this._isCodeOfConductPresented$.next(this.isCodeOfConductPresented);
        this.isMarkedAsUnread = false;
    }

    /**
     * Activates the conversation that was asked for before the conversations were known, if there is one.
     * <p>
     * Only when nothing else has been activated in the meantime: a conversation the user has since opened themselves is
     * the more recent intent and must not be replaced by the one the url happened to carry.
     */
    private activateRequestedConversation() {
        if (this.requestedConversationId === undefined || this.activeConversation) {
            this.requestedConversationId = undefined;
            return;
        }
        const requested = this.requestedConversationId;
        this.requestedConversationId = undefined;
        this.setActiveConversation(requested);
    }

    public disableConversationService() {
        this.cleanupSubscriptions();
        this._isServiceSetup$.next(false);
    }

    /**
     * Set the course conversation component to the contents of the code of conduct.
     */
    public setCodeOfConduct() {
        this.selectionVersion++;
        this.activeConversation = undefined;
        this._activeConversation$.next(this.activeConversation);
        this.isCodeOfConductPresented = true;
        this._isCodeOfConductPresented$.next(this.isCodeOfConductPresented);
    }

    public markAsRead(conversationId: number) {
        const indexOfCachedConversation = this.conversationsOfUser.findIndex((cachedConversation) => cachedConversation.id === conversationId);
        if (indexOfCachedConversation !== -1) {
            this.conversationsOfUser[indexOfCachedConversation].lastMessageDate = dayjs();
            this.conversationsOfUser[indexOfCachedConversation].unreadMessagesCount = 0;
            this.readRequestsOf(conversationId).failedContributions.clear();
            this._conversationsOfUser$.next(this.conversationsOfUser);
        }
        this.hasUnreadMessagesCheck();
    }

    public updateConversationUnreadState(conversationId: number | undefined, lastReadDate: dayjs.Dayjs | undefined, unreadMessagesCount: number | undefined) {
        if (conversationId !== undefined) {
            // The server recalculated this count. Earlier automatic reads must not alter its new baseline.
            const reads = this.readRequestsOf(conversationId);
            reads.unreadRevision++;
            reads.failedContributions.clear();
        }
        if (this.activeConversation && this.activeConversation?.id === conversationId) {
            this.activeConversation.lastReadDate = lastReadDate;
            this.activeConversation.unreadMessagesCount = unreadMessagesCount;
            this.activeConversation.hasUnreadMessage = (unreadMessagesCount ?? 0) > 0;
            this.isMarkedAsUnread = true;
        }
        const indexOfConversationToUpdate = this.conversationsOfUser.findIndex((conversation) => conversation.id === conversationId);
        if (indexOfConversationToUpdate !== -1) {
            this.conversationsOfUser[indexOfConversationToUpdate].lastReadDate = lastReadDate;
            this.conversationsOfUser[indexOfConversationToUpdate].unreadMessagesCount = unreadMessagesCount;
            this.conversationsOfUser[indexOfConversationToUpdate].hasUnreadMessage = (unreadMessagesCount ?? 0) > 0;
            this._conversationsOfUser$.next(this.conversationsOfUser);
        }
    }

    private updateConversationAsRead() {
        if (this.activeConversation && !this.isMarkedAsUnread) {
            const lastReadDate = dayjs();
            this.activeConversation.lastReadDate = lastReadDate;
            const cachedConversation = this.conversationsOfUser.find((conversation) => conversation.id === this.activeConversation!.id);
            if (cachedConversation) {
                cachedConversation.lastReadDate = lastReadDate;
            }
            this.clearActiveConversationUnreadBadge();
        }
    }

    private clearActiveConversationUnreadBadge() {
        if (this.activeConversation && !this.isMarkedAsUnread) {
            this.activeConversation.unreadMessagesCount = 0;
            this.activeConversation.hasUnreadMessage = false;
            if (this.activeConversation.id !== undefined) {
                this.readRequestsOf(this.activeConversation.id).failedContributions.clear();
            }
            const cachedConversation = this.conversationsOfUser.find((conversation) => conversation.id === this.activeConversation!.id);
            if (cachedConversation) {
                cachedConversation.unreadMessagesCount = 0;
                cachedConversation.hasUnreadMessage = false;
                this._conversationsOfUser$.next(this.conversationsOfUser);
            }
        }
    }

    public forceRefresh(notifyActiveConversationSubscribers = true, notifyConversationsSubscribers = true): Observable<never> {
        if (!this._course) {
            throw new Error('Course is not set. The service does not seem to be initialized.');
        }
        this.setIsLoading(true);
        return this.conversationService.getConversationsOfUser(this._courseId).pipe(
            map((conversations: HttpResponse<ConversationDTO[]>) => {
                return conversations.body ?? [];
            }),
            catchError((res: HttpErrorResponse) => {
                onError(this.alertService, res);
                this.setIsLoading(false);
                // Do not continue into the mapping below with an empty list. Treating a failed request as "the user has no
                // conversations" would drop the cached conversations, reset the active conversation and, through the
                // active conversation subscriber, strip the conversationId from the URL. A reload could then not restore
                // the conversation any more, because the identifier it needs is gone. Keep the last known state instead.
                // The error is passed on rather than swallowed, so that subscribers acting in their completion handler
                // do not mistake a failed refresh for an up to date list.
                return throwError(() => res);
            }),
            map((conversations: ConversationDTO[]) => {
                this.conversationsOfUser = conversations;
                // The refreshed counts are authoritative, so no earlier failure is still a local contribution.
                this.readRequests.forEach((reads) => reads.failedContributions.clear());
                this.conversationsLoaded = true;
                this.hasUnreadMessagesCheck();
                this._conversationsOfUser$.next(this.conversationsOfUser);

                // we check if the active conversation still is part of the conversations of the user, otherwise we reset it
                if (this.activeConversation) {
                    const cachedActiveConversation = this.conversationsOfUser.find((conversationInCache) => conversationInCache.id === this.activeConversation?.id);
                    if (!cachedActiveConversation) {
                        this.activeConversation = undefined;
                    } else {
                        this.activeConversation = cachedActiveConversation;
                    }
                }
                if (notifyConversationsSubscribers) {
                    this._conversationsOfUser$.next(this.conversationsOfUser);
                }
                this.activateRequestedConversation();
                if (notifyActiveConversationSubscribers) {
                    this._activeConversation$.next(this.activeConversation);
                }
                this.setIsLoading(false);
                return;
            }),
            finalize(() => {
                this.setIsLoading(false);
            }),
            // refresh complete
            switchMap(() => EMPTY),
        );
    }

    public createOneToOneChat = (loginOfChatPartner: string): Observable<HttpResponse<OneToOneChatDTO>> =>
        this.onConversationCreation(this.oneToOneChatService.create(this._courseId, loginOfChatPartner));
    public createOneToOneChatWithId = (userId: number): Observable<HttpResponse<OneToOneChatDTO>> =>
        this.onConversationCreation(this.oneToOneChatService.createWithId(this._courseId, userId));
    public createChannel = (channel: ChannelDTO) => this.onConversationCreation(this.channelService.create(this._courseId, channel));
    public createGroupChat = (loginsOfChatPartners: string[]) => this.onConversationCreation(this.groupChatService.create(this._courseId, loginsOfChatPartners));
    private onConversationCreation = (creation$: Observable<HttpResponse<ConversationDTO>>): Observable<never> => {
        return creation$.pipe(
            tap((conversation: HttpResponse<ConversationDTO>) => {
                this.selectionVersion++;
                this.activeConversation = conversation.body!;
            }),
            catchError((res: HttpErrorResponse) => {
                if (!res.error?.skipAlert) {
                    return of(null);
                }

                onError(this.alertService, res);
                if (res.error && res.error.title) {
                    this.alertService.addErrorAlert(res.error.title, res.error.message, res.error.params);
                } else {
                    onError(this.alertService, res);
                }
                this.setIsLoading(false);
                return of(null);
            }),
            switchMap(() => {
                return this.forceRefresh();
            }),
        );
    };

    public createGroupConversation(loginsOfChatPartners: string[]): Observable<HttpResponse<ConversationDTO>> {
        return this.groupChatService.create(this._courseId, loginsOfChatPartners);
    }

    public createDirectConversation(loginsOfChatPartners: string): Observable<HttpResponse<ConversationDTO>> {
        return this.oneToOneChatService.create(this._courseId, loginsOfChatPartners);
    }

    public setUpConversationService = (course: Course): Observable<never> => {
        this._courseId = course.id!;
        this._course = course;
        return this.conversationService.getConversationsOfUser(this._courseId).pipe(
            map((conversations: HttpResponse<ConversationDTO[]>) => {
                return conversations.body ?? [];
            }),
            catchError((res: HttpErrorResponse) => {
                onError(this.alertService, res);
                this.setIsLoading(false);
                this._isServiceSetup$.next(false);
                // Stop here instead of running the setup below with an empty list, which would announce the service as
                // ready while no conversation is known. Every conversation opened from the URL would then be reported
                // as one the user is not a member of, and the view would stay empty. The error is passed on so that the
                // caller does not record the service as instantiated and can set it up again later.
                return throwError(() => res);
            }),
            map((conversations: ConversationDTO[]) => {
                this.conversationsOfUser = conversations;
                // The refreshed counts are authoritative, so no earlier failure is still a local contribution.
                this.readRequests.forEach((reads) => reads.failedContributions.clear());
                this.conversationsLoaded = true;
                this.hasUnreadMessagesCheck();
                this._conversationsOfUser$.next(this.conversationsOfUser);
                this.activeConversation = undefined;
                // After the reset, so the conversation the url asked for survives it. This is the reload case: the
                // query parameters were read while this request was still in flight.
                this.activateRequestedConversation();
                this._activeConversation$.next(this.activeConversation);
                this.subscribeToConversationMembershipTopic(course.id!, this.userId);
                this.subscribeToRouteChange();
                this.setIsLoading(false);
                this._isServiceSetup$.next(true);
                return;
            }),
            finalize(() => {
                this.setIsLoading(false);
            }),
            // service is ready to use and cached values can be received via the respective replay subjects
            switchMap(() => EMPTY),
        );
    };

    checkForUnreadMessages = (course: Course) => {
        if (!course?.id) {
            return;
        }

        this.conversationService.checkForUnreadMessages(course.id).subscribe({
            next: (hasNewMessages) => {
                if (hasNewMessages?.body !== this.hasUnreadMessages) {
                    this.hasUnreadMessages = hasNewMessages?.body ?? false;
                    this._hasUnreadMessages$.next(this.hasUnreadMessages);
                }
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
            },
        });
    };

    acceptCodeOfConduct(course: Course) {
        if (!course?.id) {
            return;
        }

        this.conversationService.acceptCodeOfConduct(course.id).subscribe({
            next: () => {
                this.isCodeOfConductAccepted = true;
                this._isCodeOfConductAccepted$.next(true);
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
            },
        });
    }

    checkIsCodeOfConductAccepted(course: Course) {
        if (!course?.id) {
            return;
        }

        this.conversationService.checkIsCodeOfConductAccepted(course.id).subscribe({
            next: (response) => {
                if (response.body !== null) {
                    this.isCodeOfConductAccepted = response.body;
                    this._isCodeOfConductAccepted$.next(this.isCodeOfConductAccepted);
                }
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
            },
        });
    }

    private hasUnreadMessagesCheck = (): void => {
        const hasNewMessages = this.conversationsOfUser.some((conversation) => {
            return conversation?.unreadMessagesCount && conversation.unreadMessagesCount > 0 && !conversation.isMuted;
        });
        if (hasNewMessages !== this.hasUnreadMessages) {
            this.hasUnreadMessages = hasNewMessages;
            this._hasUnreadMessages$.next(this.hasUnreadMessages);
        }
        if (hasNewMessages) {
            this.updateUnread();
        }
    };

    private updateUnread() {
        this.conversationsOfUser.forEach((conversation) => (conversation.hasUnreadMessage = !!conversation.unreadMessagesCount && conversation.unreadMessagesCount > 0));
        this._conversationsOfUser$.next(this.conversationsOfUser);
    }

    private setIsLoading(value: boolean) {
        this.isLoading = value;
        this._isLoading$.next(this.isLoading);
    }

    /**
     * Via this Topic, users are informed about changes to which conversations they are a part of
     *
     * Users will be notified via this topic about the following events:
     * - OneToOneChats: When the creator of the one to one chat starts the conversation by sending the first message
     * - Channels/GroupChats: When the user is added to the channel or group chat (channel or group chat shows up when user is added)
     */
    private getConversationMembershipTopic(courseId: number, userId: number) {
        const courseTopicName = '/user' + CommunicationWebsocketChannelPrefix + 'courses/' + courseId;
        return courseTopicName + '/conversations/user/' + userId;
    }

    private subscribeToRouteChange() {
        this.router.events.subscribe((event) => {
            if (event instanceof NavigationEnd) {
                // Navigation clears badges; only successful reads advance the cached timestamp.
                // The message view retains its opening timestamp for initial positioning.
                if (this.isReadingConversation(this.activeConversation?.id) && !this.isMarkedAsUnread) {
                    this.clearActiveConversationUnreadBadge();
                    this.hasUnreadMessagesCheck();
                }
            }
        });
    }
    private subscribeToConversationMembershipTopic(courseId: number, userId: number) {
        // already subscribed to the topic -> nothing to do
        if (this.subscribedConversationMembershipTopic) {
            return;
        }

        const conversationMembershipTopic = this.getConversationMembershipTopic(courseId, userId);
        this.subscribedConversationMembershipTopic = conversationMembershipTopic;

        this.conversationMembershipSubscription = this.websocketService
            .subscribe<ConversationWebsocketDTO>(conversationMembershipTopic)
            .subscribe((websocketDTO: ConversationWebsocketDTO) => {
                this.onConversationMembershipMessageReceived(websocketDTO);
            });
    }

    /**
     * Tells the service whether the conversation view is shown. The service belongs to the whole course, so it outlives the view and keeps the
     * selected conversation for the way back to it, e.g. while the user is on the exercises tab of the course.
     *
     * @param isOpen whether the conversation view is shown
     */
    public setConversationViewOpen(isOpen: boolean) {
        this.isConversationViewOpen = isOpen;
    }

    /**
     * Whether an explicit unread mark currently determines this conversation's read boundary.
     * @param conversationId the conversation shown by the message view
     * @returns whether its current timestamp should replace the opening boundary
     */
    public isConversationMarkedAsUnread(conversationId: number | undefined): boolean {
        return conversationId !== undefined && this.activeConversation?.id === conversationId && this.isMarkedAsUnread;
    }

    private readRequestsOf(conversationId: number) {
        let readRequest = this.readRequests.get(conversationId);
        if (!readRequest) {
            readRequest = { sent: 0, succeeded: 0, failed: 0, unreadRevision: 0, failedContributions: new Set<number>() };
            this.readRequests.set(conversationId, readRequest);
        }
        return readRequest;
    }

    /**
     * Notes that the messages of a conversation are being requested. The server resets the unread count of the conversation with that
     * request, which also covers every read request that was sent for the conversation before it.
     *
     * @param conversationId the conversation whose messages are requested
     * @return the pending-read coverage and unread revision to pass to {@link messagesLoaded} once the messages arrive
     */
    public messagesRequested(conversationId: number): ConversationReadRequest {
        const state = this.readRequestsOf(conversationId);
        return { coveredRequests: state.sent, unreadRevision: state.unreadRevision };
    }

    /**
     * Notes that the messages of a conversation arrived, so the read requests sent before they were requested no longer describe the unread count
     * of the server, whether a covered request failed before or after the messages arrived.
     *
     * @param conversationId the conversation whose messages arrived
     * @param request         the value {@link messagesRequested} returned when the request started
     * @param readAt          when the successful read was requested, so later arrivals remain unread on return
     */
    public messagesLoaded(conversationId: number, request: ConversationReadRequest, readAt = dayjs()) {
        const readRequest = this.readRequestsOf(conversationId);
        // Manual unread marks remain authoritative even after the user navigates to another conversation.
        if (readRequest.unreadRevision !== request.unreadRevision) {
            return;
        }
        readRequest.succeeded = Math.max(readRequest.succeeded, request.coveredRequests);
        let coveredFailures = 0;
        for (const failedRequest of readRequest.failedContributions) {
            if (failedRequest <= request.coveredRequests) {
                readRequest.failedContributions.delete(failedRequest);
                coveredFailures++;
            }
        }
        if (coveredFailures) {
            const cached = this.conversationsOfUser.find((conversation) => conversation.id === conversationId);
            const active = this.activeConversation?.id === conversationId ? this.activeConversation : undefined;
            for (const conversation of new Set([cached, active])) {
                if (conversation) {
                    conversation.unreadMessagesCount = Math.max(0, (conversation.unreadMessagesCount ?? 0) - coveredFailures);
                    conversation.hasUnreadMessage = conversation.unreadMessagesCount > 0;
                }
            }
            this.hasUnreadMessagesCheck();
            this._conversationsOfUser$.next(this.conversationsOfUser);
        }
        this.advanceConversationReadDate(conversationId, readAt);
    }

    private advanceConversationReadDate(conversationId: number, readAt: dayjs.Dayjs) {
        if (this.activeConversation?.id === conversationId && this.isMarkedAsUnread) {
            return;
        }
        const cached = this.conversationsOfUser.find((conversation) => conversation.id === conversationId);
        if (cached && (!cached.lastReadDate || readAt.isAfter(cached.lastReadDate))) {
            cached.lastReadDate = readAt;
        }
        if (this.activeConversation?.id === conversationId && (!this.activeConversation.lastReadDate || readAt.isAfter(this.activeConversation.lastReadDate))) {
            this.activeConversation.lastReadDate = readAt;
        }
    }

    // The selected conversation only counts as read while the view that shows it is open.
    private isReadingConversation(conversationId: number | undefined): boolean {
        return this.isConversationViewOpen && conversationId !== undefined && conversationId === this.activeConversation?.id;
    }

    private onConversationMembershipMessageReceived(websocketDTO: ConversationWebsocketDTO) {
        const conversationDTO = this.conversationService.convertServerDates(websocketDTO.conversation);
        const action = websocketDTO.action;

        switch (action) {
            case CommunicationCrudAction.CREATE:
                this.handleCreateConversation(conversationDTO);
                break;
            case CommunicationCrudAction.UPDATE:
                this.handleUpdateConversation(conversationDTO);
                break;
            case CommunicationCrudAction.DELETE:
                this.handleDeleteConversation(conversationDTO);
                break;
            case CommunicationCrudAction.NEW_MESSAGE:
                if (this.isReadingConversation(conversationDTO.id) && !this.isMarkedAsUnread) {
                    // The user is reading this conversation, so what arrived is not new to them, e.g. a reply in a thread of theirs. The server counted
                    // it for them anyway, and reading the conversation again is what resets that count.
                    const selectionVersion = this.selectionVersion;
                    const conversationId = conversationDTO.id!;
                    const readRequest = this.readRequestsOf(conversationId);
                    const requestNumber = ++readRequest.sent;
                    const unreadRevision = readRequest.unreadRevision;
                    const readAt = dayjs();
                    this.conversationService.markAsRead(this._courseId, conversationId).subscribe({
                        next: () => {
                            if (readRequest.unreadRevision !== unreadRevision) {
                                return;
                            }
                            readRequest.succeeded = Math.max(readRequest.succeeded, requestNumber);
                            // A newer read that failed left a count on the server, which a delayed older success must not hide.
                            const isSupersededByFailure = readRequest.failed > requestNumber;
                            if (!isSupersededByFailure) {
                                this.advanceConversationReadDate(conversationId, readAt);
                            }
                            // A delayed read must not clear a different selection or an explicit unread mark.
                            if (!isSupersededByFailure && selectionVersion === this.selectionVersion && this.isReadingConversation(conversationId) && !this.isMarkedAsUnread) {
                                // The message view keeps its own opening timestamp for the New divider.
                                this.clearActiveConversationUnreadBadge();
                                this.hasUnreadMessagesCheck();
                            }
                        },
                        error: () => {
                            // A newer read, or the messages request that covers this one, already reset the count on the server, so this failure no
                            // longer describes it. A request cannot both succeed and fail, so its own number is never recorded as succeeded.
                            if (readRequest.unreadRevision !== unreadRevision || readRequest.succeeded >= requestNumber) {
                                return;
                            }
                            readRequest.failed = Math.max(readRequest.failed, requestNumber);
                            readRequest.failedContributions.add(requestNumber);
                            // the count the server raised stays, so the badge has to show it
                            this.handleNewMessage(conversationId, conversationDTO.lastMessageDate);
                        },
                    });
                } else {
                    this.handleNewMessage(conversationDTO.id, conversationDTO.lastMessageDate);
                }
                break;
        }
        this._conversationsOfUser$.next(this.conversationsOfUser);
    }

    private handleCreateConversation(createdConversation: ConversationDTO) {
        this.handleUpdateOrCreate(createdConversation);
    }

    private handleUpdateConversation(updatedConversation: ConversationDTO) {
        this.handleUpdateOrCreate(updatedConversation);
    }

    private handleUpdateOrCreate(updatedOrNewConversation: ConversationDTO) {
        const conversationsCopy = [...this.conversationsOfUser];
        const indexOfCachedConversation = conversationsCopy.findIndex((cachedConversation) => cachedConversation.id === updatedOrNewConversation.id);
        if (indexOfCachedConversation === -1) {
            // conversation is not yet cached -> add it
            conversationsCopy.push(updatedOrNewConversation);
        } else {
            // conversation is already cached -> update it
            conversationsCopy[indexOfCachedConversation] = updatedOrNewConversation;
        }
        this.conversationsOfUser = conversationsCopy;
        this.hasUnreadMessagesCheck();

        // Note: We do not update the active conversation here because it would cause a UI refresh for all users whenever
        // for example a new users joins.
        // This would disrupt the user experience, because the user might be in the middle of writing a message.
        // Therefore we live with a small inconsistency until the users opens the conversation again.
    }

    private handleDeleteConversation(deletedConversation: ConversationDTO) {
        const conversationsCopy = [...this.conversationsOfUser];
        const indexOfCachedConversation = conversationsCopy.findIndex((cachedConversation) => cachedConversation.id === deletedConversation.id);
        if (indexOfCachedConversation !== -1) {
            // conversation is cached -> remove it
            conversationsCopy.splice(indexOfCachedConversation, 1);
        }
        this.conversationsOfUser = conversationsCopy;

        if (this.activeConversation?.id === deletedConversation.id) {
            this.activeConversation = undefined;
            this._activeConversation$.next(this.activeConversation);
        }
    }

    handleNewMessage(conversationId: number | undefined, lastMessageDate: dayjs.Dayjs | undefined) {
        const conversationsCopy = [...this.conversationsOfUser];
        const indexOfCachedConversation = conversationsCopy.findIndex((cachedConversation) => cachedConversation.id === conversationId);
        if (indexOfCachedConversation !== -1) {
            conversationsCopy[indexOfCachedConversation].lastMessageDate = lastMessageDate;
            conversationsCopy[indexOfCachedConversation].hasUnreadMessage = true;
            conversationsCopy[indexOfCachedConversation].unreadMessagesCount = (conversationsCopy[indexOfCachedConversation].unreadMessagesCount ?? 0) + 1;
            if (!this.hasUnreadMessages) {
                this.hasUnreadMessages = true;
                this._hasUnreadMessages$.next(this.hasUnreadMessages);
            }
        }
        this.conversationsOfUser = conversationsCopy;
        this._conversationsOfUser$.next(this.conversationsOfUser);
    }

    static getQueryParamsForConversation(conversationId: number): Params {
        const params: Params = {};
        params.conversationId = conversationId;
        return params;
    }

    static getLinkForConversation(courseId: number): RouteComponents {
        return ['/courses', courseId, 'communication'];
    }

    markAllChannelsAsRead(course: Course | undefined) {
        if (!course?.id) {
            return of();
        }

        const readAt = dayjs();
        const coveredReads = this.conversationsOfUser
            .filter((conversation) => conversation.id !== undefined)
            .map((conversation) => {
                return { conversationId: conversation.id!, request: this.messagesRequested(conversation.id!) };
            });

        this.conversationsOfUser = this.conversationsOfUser.map((conversation) => {
            conversation.hasUnreadMessage = false;
            conversation.unreadMessagesCount = 0;
            if (conversation.id !== undefined) {
                this.readRequestsOf(conversation.id).failedContributions.clear();
            }
            return conversation;
        });

        this._conversationsOfUser$.next(this.conversationsOfUser);

        return this.conversationService.markAllChannelsAsRead(course.id).pipe(
            tap(() => {
                for (const read of coveredReads) {
                    this.messagesLoaded(read.conversationId, read.request, readAt);
                }
            }),
            catchError((errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                // The failed PATCH did not confirm the optimistic reset, for any conversation type.
                // Refresh membership state without reopening the active conversation and its messages.
                return this.forceRefresh(false).pipe(catchError(() => EMPTY));
            }),
        );
    }
}
