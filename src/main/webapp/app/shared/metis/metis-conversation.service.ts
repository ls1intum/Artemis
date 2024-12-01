import { Injectable, OnDestroy } from '@angular/core';
import { EMPTY, Observable, ReplaySubject, Subject, Subscription, catchError, finalize, map, of, switchMap, tap } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ConversationWebsocketDTO } from 'app/entities/metis/conversation/conversation-websocket-dto.model';
import { MetisPostAction, MetisWebsocketChannelPrefix, RouteComponents } from 'app/shared/metis/metis.util';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { OneToOneChatService } from 'app/shared/metis/conversations/one-to-one-chat.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/entities/course.model';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import dayjs from 'dayjs/esm';
import { NavigationEnd, Params, Router } from '@angular/router';
import { MetisPostDTO } from 'app/entities/metis/metis-post-dto.model';
import { NotificationService } from 'app/shared/notification/notification.service';

/**
 * NOTE: NOT INJECTED IN THE ROOT MODULE
 */
@Injectable()
export class MetisConversationService implements OnDestroy {
    // Stores the conversation of the course where the current user is a member
    private conversationsOfUser: ConversationDTO[] = [];
    _conversationsOfUser$: ReplaySubject<ConversationDTO[]> = new ReplaySubject<ConversationDTO[]>(1);
    // Stores the currently selected conversation
    private activeConversation: ConversationDTO | undefined = undefined;
    _activeConversation$: ReplaySubject<ConversationDTO | undefined> = new ReplaySubject<ConversationDTO | undefined>(1);
    private isCodeOfConductAccepted: boolean = false;
    _isCodeOfConductAccepted$: ReplaySubject<boolean> = new ReplaySubject<boolean>(1);
    private isCodeOfConductPresented: boolean = false;
    _isCodeOfConductPresented$: ReplaySubject<boolean> = new ReplaySubject<boolean>(1);
    private hasUnreadMessages = false;
    _hasUnreadMessages$: Subject<boolean> = new ReplaySubject<boolean>(1);
    // Stores the course for which the service is set up -> should not change during the lifetime of the service
    private _course: Course | undefined = undefined;
    // Stores if the service is currently loading data
    private isLoading = false;
    _isLoading$: ReplaySubject<boolean> = new ReplaySubject<boolean>(1);

    private subscribedConversationMembershipTopic?: string;
    private activeConversationSubscription?: Subscription;

    private userId: number;
    private _courseId: number;

    private _isServiceSetup$: ReplaySubject<boolean> = new ReplaySubject<boolean>(1);

    constructor(
        private groupChatService: GroupChatService,
        private oneToOneChatService: OneToOneChatService,
        private channelService: ChannelService,
        protected conversationService: ConversationService,
        private jhiWebsocketService: JhiWebsocketService,
        private accountService: AccountService,
        private alertService: AlertService,
        private router: Router,
        private notificationService: NotificationService,
    ) {
        this.accountService.identity().then((user: User) => {
            this.userId = user.id!;
        });

        this.activeConversationSubscription = this.notificationService.newOrUpdatedMessage.subscribe((postDTO: MetisPostDTO) => {
            if (postDTO.action === MetisPostAction.CREATE && postDTO.post.author?.id !== this.userId) {
                this.handleNewMessage(postDTO.post.conversation?.id, postDTO.post.conversation?.lastMessageDate);
            }
        });
    }

    ngOnDestroy(): void {
        if (this.subscribedConversationMembershipTopic) {
            this.jhiWebsocketService.unsubscribe(this.subscribedConversationMembershipTopic);
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
        this.updateLastReadDateAndNumberOfUnreadMessages();
        let cachedConversation: ConversationDTO | undefined = undefined;
        if (conversationIdentifier) {
            const parameterJustId = typeof conversationIdentifier === 'number';
            cachedConversation = this.conversationsOfUser.find(
                (conversationInCache) => conversationInCache.id === (parameterJustId ? conversationIdentifier : conversationIdentifier.id),
            );
        }
        if (!cachedConversation && conversationIdentifier) {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'artemisApp.metis.channel.notAMember',
            });
        }
        this.activeConversation = cachedConversation;
        this._activeConversation$.next(this.activeConversation);
        this.isCodeOfConductPresented = false;
        this._isCodeOfConductPresented$.next(this.isCodeOfConductPresented);
    }

    /**
     * Set the course conversation component to the contents of the code of conduct.
     */
    public setCodeOfConduct() {
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
        }
        this.hasUnreadMessagesCheck();
    }

    private updateLastReadDateAndNumberOfUnreadMessages() {
        // update last read date and number of unread messages of the conversation that is currently active before switching to another conversation
        if (this.activeConversation) {
            this.activeConversation.lastReadDate = dayjs();
            this.activeConversation.unreadMessagesCount = 0;
            this.activeConversation.hasUnreadMessage = false;
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
                return of([]);
            }),
            map((conversations: ConversationDTO[]) => {
                this.conversationsOfUser = conversations;
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
    public createChannel = (channel: ChannelDTO) => this.onConversationCreation(this.channelService.create(this._courseId, channel));
    public createGroupChat = (loginsOfChatPartners: string[]) => this.onConversationCreation(this.groupChatService.create(this._courseId, loginsOfChatPartners));
    private onConversationCreation = (creation$: Observable<HttpResponse<ConversationDTO>>): Observable<never> => {
        return creation$.pipe(
            tap((conversation: HttpResponse<ConversationDTO>) => {
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
                return of([]);
            }),
            map((conversations: ConversationDTO[]) => {
                this.conversationsOfUser = conversations;
                this.hasUnreadMessagesCheck();
                this._conversationsOfUser$.next(this.conversationsOfUser);
                this.activeConversation = undefined;
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
            return conversation?.unreadMessagesCount && conversation.unreadMessagesCount > 0;
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
        const courseTopicName = '/user' + MetisWebsocketChannelPrefix + 'courses/' + courseId;
        return courseTopicName + '/conversations/user/' + userId;
    }

    private subscribeToRouteChange() {
        this.router.events.subscribe((event) => {
            if (event instanceof NavigationEnd) {
                // update last read date and number of unread messages of the conversation that is currently active before switching to another conversation
                if (this.activeConversation) {
                    this.activeConversation.lastReadDate = dayjs();
                    this.activeConversation.unreadMessagesCount = 0;
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
        this.jhiWebsocketService.subscribe(conversationMembershipTopic);
        this.subscribedConversationMembershipTopic = conversationMembershipTopic;

        this.jhiWebsocketService.receive(conversationMembershipTopic).subscribe((websocketDTO: ConversationWebsocketDTO) => {
            this.onConversationMembershipMessageReceived(websocketDTO);
        });
    }

    private onConversationMembershipMessageReceived(websocketDTO: ConversationWebsocketDTO) {
        const conversationDTO = this.conversationService.convertServerDates(websocketDTO.conversation);
        const action = websocketDTO.action;

        switch (action) {
            case MetisPostAction.CREATE:
                this.handleCreateConversation(conversationDTO);
                break;
            case MetisPostAction.UPDATE:
                this.handleUpdateConversation(conversationDTO);
                break;
            case MetisPostAction.DELETE:
                this.handleDeleteConversation(conversationDTO);
                break;
            case MetisPostAction.NEW_MESSAGE:
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

    private handleNewMessage(conversationId: number | undefined, lastMessageDate: dayjs.Dayjs | undefined) {
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
    }

    static getQueryParamsForConversation(conversationId: number): Params {
        const params: Params = {};
        params.conversationId = conversationId;
        return params;
    }

    static getLinkForConversation(courseId: number): RouteComponents {
        return ['/courses', courseId, 'communication'];
    }
}
