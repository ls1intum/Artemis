import { Injectable, OnDestroy, inject } from '@angular/core';
import { EMPTY, Observable, ReplaySubject, Subject, Subscription, catchError, defer, finalize, map, of, switchMap, tap, throwError } from 'rxjs';
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
import { Params } from '@angular/router';
import { PostBroadcastDTO } from 'app/communication/shared/entities/post-broadcast-dto.model';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';
import { hydrate } from 'app/foundation/util/deep-clone.util';

/** Identifies the manual-unread boundary when a read starts. */
export interface ConversationReadRequest {
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
    // All unread mutations invalidate in-flight list snapshots. Counts and timestamps come from the server;
    // no request-specific contributions are added to or subtracted from a refreshed count.
    private unreadStateVersion = 0;
    private manualUnreadRevisions = new Map<number, number>();
    private unreadRefresh?: Subscription;
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
        this.unreadRefresh?.unsubscribe();
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
        // Query-only changes (e.g. opening a thread) do not select a new conversation or clear a manual unread mark.
        if (cachedConversation && this.activeConversation?.id === conversationId) {
            return;
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

    public updateConversationUnreadState(conversationId: number | undefined, lastReadDate: dayjs.Dayjs | undefined, unreadMessagesCount: number | undefined) {
        this.unreadStateVersion++;
        if (conversationId !== undefined) {
            this.manualUnreadRevisions.set(conversationId, (this.manualUnreadRevisions.get(conversationId) ?? 0) + 1);
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
        this.hasUnreadMessagesCheck();
    }

    /** Never publish an unread snapshot captured before a newer read, incoming message or explicit unread mark. */
    private currentUnreadSnapshot<T>(request: () => Observable<T>): Observable<T> {
        return defer(() => {
            const version = this.unreadStateVersion;
            return request().pipe(switchMap((response) => (version === this.unreadStateVersion ? of(response) : this.currentUnreadSnapshot(request))));
        });
    }

    private loadConversations(): Observable<ConversationDTO[]> {
        return this.currentUnreadSnapshot(() => this.conversationService.getConversationsOfUser(this._courseId)).pipe(map((response) => response.body ?? []));
    }

    public forceRefresh(notifyActiveConversationSubscribers = true, notifyConversationsSubscribers = true): Observable<never> {
        if (!this._course) {
            throw new Error('Course is not set. The service does not seem to be initialized.');
        }
        this.setIsLoading(true);
        return this.loadConversations().pipe(
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
                this.conversationsLoaded = true;
                this.hasUnreadMessagesCheck();
                this._conversationsOfUser$.next(this.conversationsOfUser);

                // we check if the active conversation still is part of the conversations of the user, otherwise we reset it
                if (this.activeConversation) {
                    const cachedActiveConversation = this.conversationsOfUser.find((conversationInCache) => conversationInCache.id === this.activeConversation?.id);
                    if (!cachedActiveConversation) {
                        this.activeConversation = undefined;
                    } else {
                        // Keep the message view's DTO identity across all refreshes: its opening divider stays fixed, and a later
                        // manual unread mark must still update the object held by that view.
                        hydrate(this.activeConversation, cachedActiveConversation);
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
        return this.loadConversations().pipe(
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
                this.conversationsLoaded = true;
                this.hasUnreadMessagesCheck();
                this._conversationsOfUser$.next(this.conversationsOfUser);
                this.activeConversation = undefined;
                // After the reset, so the conversation the url asked for survives it. This is the reload case: the
                // query parameters were read while this request was still in flight.
                this.activateRequestedConversation();
                this._activeConversation$.next(this.activeConversation);
                this.subscribeToConversationMembershipTopic(course.id!, this.userId);
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

        this.currentUnreadSnapshot(() => this.conversationService.checkForUnreadMessages(course.id!)).subscribe({
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

    /** Capture the manual boundary before a PATCH or a messages GET (which also marks the conversation read). */
    public messagesRequested(conversationId: number): ConversationReadRequest {
        this.unreadStateVersion++;
        return { unreadRevision: this.manualUnreadRevisions.get(conversationId) ?? 0 };
    }

    /** A successful messages GET changed server read state even if its posts were superseded by navigation. */
    public messagesLoaded(conversationId: number, request: ConversationReadRequest) {
        this.readCompleted(conversationId, request);
    }

    private readCompleted(conversationId: number, request: ConversationReadRequest) {
        this.unreadStateVersion++;
        // An older read outcome must not undo a later explicit mark, including after navigation.
        if (request.unreadRevision === (this.manualUnreadRevisions.get(conversationId) ?? 0)) {
            this.refreshUnreadState();
        }
    }

    private refreshUnreadState() {
        // One internal refresh at a time. Any mutation during it invalidates its snapshot, so its
        // response will repeat the GET instead of losing that mutation or starting parallel GETs.
        // Exercise discussions can read messages before the communication view initializes this cache.
        // Its first load will obtain the authoritative state; there is no cached list to reconcile yet.
        if (this.conversationsLoaded && (!this.unreadRefresh || this.unreadRefresh.closed)) {
            this.unreadRefresh = this.forceRefresh(false).subscribe({ error: () => {} });
        }
    }

    /** Marks a live arrival read only while its conversation is visible and has no explicit unread mark. */
    private readNewMessage(conversationId: number | undefined) {
        if (!this.isReadingConversation(conversationId) || this.isMarkedAsUnread) {
            return;
        }
        const request = this.messagesRequested(conversationId!);
        this.conversationService.markAsRead(this._courseId, conversationId!).subscribe({
            next: () => this.readCompleted(conversationId!, request),
            // A rejected read leaves the server count intact. Fetch it rather than inventing a contribution.
            error: () => this.readCompleted(conversationId!, request),
        });
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
                this.handleNewMessage(conversationDTO.id, conversationDTO.lastMessageDate);
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
        this.unreadStateVersion++;
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
        this.unreadStateVersion++;
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
        this.unreadStateVersion++;
        if (this.isReadingConversation(conversationId) && !this.isMarkedAsUnread) {
            this.readNewMessage(conversationId);
            return;
        }
        const conversationsCopy = [...this.conversationsOfUser];
        const indexOfCachedConversation = conversationsCopy.findIndex((cachedConversation) => cachedConversation.id === conversationId);
        if (indexOfCachedConversation !== -1) {
            conversationsCopy[indexOfCachedConversation].lastMessageDate = lastMessageDate;
            conversationsCopy[indexOfCachedConversation].hasUnreadMessage = true;
            conversationsCopy[indexOfCachedConversation].unreadMessagesCount = (conversationsCopy[indexOfCachedConversation].unreadMessagesCount ?? 0) + 1;
            if (this.activeConversation && this.activeConversation.id === conversationId) {
                hydrate(this.activeConversation, conversationsCopy[indexOfCachedConversation]);
            }
        }
        this.conversationsOfUser = conversationsCopy;
        this.hasUnreadMessagesCheck();
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

        this.unreadStateVersion++;
        return this.conversationService.markAllChannelsAsRead(course.id).pipe(
            tap(() => {
                this.unreadStateVersion++;
                this.refreshUnreadState();
            }),
            catchError((errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                this.unreadStateVersion++;
                return this.forceRefresh(false).pipe(catchError(() => EMPTY));
            }),
        );
    }
}
