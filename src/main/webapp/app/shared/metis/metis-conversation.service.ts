import { Injectable, OnDestroy } from '@angular/core';
import { map, Observable, of, ReplaySubject } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ConversationWebsocketDTO } from 'app/entities/metis/conversation/conversation-websocket-dto.model';
import { MetisPostAction, MetisWebsocketChannelPrefix } from 'app/shared/metis/metis.util';
import { Conversation, ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { catchError } from 'rxjs/operators';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { isGroupChat } from 'app/entities/metis/conversation/groupChat.model';
import { isChannel } from 'app/entities/metis/conversation/channel.model';

/**
 * NOTE: NOT INJECTED IN THE ROOT MODULE
 */
@Injectable()
export class MetisConversationService implements OnDestroy {
    private conversationCache: ConversationDto[] = [];
    private conversationsOfUser$: ReplaySubject<ConversationDto[]> = new ReplaySubject<ConversationDto[]>(1);

    // ToDo: Why do we need this?
    private conversation$ = new ReplaySubject<ConversationDto>(1);

    private subscribedConversationMembershipTopic?: string;
    userId: number;

    constructor(
        private groupChatService: GroupChatService,
        private channelService: ChannelService,
        protected conversationService: ConversationService,
        private jhiWebsocketService: JhiWebsocketService,
        protected accountService: AccountService,
        protected alertService: AlertService,
    ) {
        this.accountService.identity().then((user: User) => {
            this.userId = user.id!;
        });
    }

    ngOnDestroy(): void {
        if (this.subscribedConversationMembershipTopic) {
            this.jhiWebsocketService.unsubscribe(this.subscribedConversationMembershipTopic);
            this.subscribedConversationMembershipTopic = undefined;
        }
    }

    // ToDo: This method is currently just called with the group chat .
    //  Maybe it should be removed and the group chat should be handled in the same way as the channel or vice versa??
    createConversation(courseId: number, conversation: Conversation): Observable<ConversationDto> {
        let createConversationObservable: Observable<HttpResponse<ConversationDto>>;
        if (isGroupChat(conversation)) {
            createConversationObservable = this.groupChatService.create(courseId, conversation);
        } else if (isChannel(conversation)) {
            createConversationObservable = this.channelService.create(courseId, conversation);
        } else {
            throw new Error('Conversation type not supported');
        }

        createConversationObservable.pipe(map((res: HttpResponse<ConversationDto>) => res.body!)).subscribe({
            next: (receivedConversation: ConversationDto) => {
                this.conversationCache.unshift(receivedConversation);
                this.conversationsOfUser$.next(this.conversationCache);
                // ToDo: Why do we need this?
                this.conversation$.next(receivedConversation);
            },
            error: (res: HttpErrorResponse) => {
                if (res.error && res.error.title) {
                    this.alertService.addErrorAlert(res.error.title, res.error.message, res.error.params);
                } else {
                    onError(this.alertService, res);
                }
            },
        });
        // ToDo: Why do we need this?
        return this.conversation$;
    }

    get conversations$(): Observable<ConversationDto[]> {
        return this.conversationsOfUser$.asObservable();
    }

    setUpConversationService(courseId: number): Observable<ConversationDto[]> {
        return this.conversationService.getConversationsOfUser(courseId).pipe(
            map((res: HttpResponse<ConversationDto[]>) => {
                this.conversationCache = res.body!;
                this.conversationsOfUser$.next(this.conversationCache);
                this.subscribeToConversationMembershipTopic(courseId, this.userId);
                return this.conversationCache;
            }),
        );
    }

    subscribeToConversationMembershipTopic(courseId: number, userId: number) {
        // already subscribed to the topic -> nothing to do
        if (this.subscribedConversationMembershipTopic) {
            return;
        }

        const conversationMembershipTopic = this.getConversationMembershipTopic(courseId, userId);
        this.jhiWebsocketService.subscribe(conversationMembershipTopic);
        this.subscribedConversationMembershipTopic = conversationMembershipTopic;

        this.jhiWebsocketService.receive(conversationMembershipTopic).subscribe((websocketDTO: ConversationWebsocketDTO) => {
            this.onWebsocketMessageReceived(websocketDTO);
        });
    }

    private onWebsocketMessageReceived(websocketDTO: ConversationWebsocketDTO) {
        const conversationDTO = this.conversationService.convertServerDates(websocketDTO.conversation);
        const action = websocketDTO.crudAction;

        switch (action) {
            case MetisPostAction.CREATE:
                this.handleCreateConversation(conversationDTO);
                break;
            case MetisPostAction.UPDATE:
                this.handleUpdateConversation(conversationDTO);
                break;
            case MetisPostAction.READ_CONVERSATION:
                this.handleReadConversation(conversationDTO);
                break;
            case MetisPostAction.DELETE:
                this.handleDeleteConversation(conversationDTO);
                break;
        }
        this.conversationsOfUser$.next(this.conversationCache);
    }

    private handleCreateConversation(createdConversation: ConversationDto) {
        const indexOfCachedConversation = this.conversationCache.findIndex((cachedConversation) => cachedConversation.id === createdConversation.id);
        if (indexOfCachedConversation === -1) {
            this.conversationCache.push(createdConversation);
        } else {
            console.error('Conversation with id ' + createdConversation.id + " already exists in cache, but was sent as 'CREATE' action");
            this.conversationCache[indexOfCachedConversation] = createdConversation;
        }
    }

    private handleDeleteConversation(deletedConversation: ConversationDto) {
        const indexOfCachedConversation = this.conversationCache.findIndex((cachedConversation) => cachedConversation.id === deletedConversation.id);
        if (indexOfCachedConversation !== -1) {
            this.conversationCache.splice(indexOfCachedConversation, 1);
        } else {
            console.error('Conversation with id ' + deletedConversation.id + " doesn't exist in cache, but was sent as 'DELETE' action");
        }
    }

    private handleUpdateConversation(updatedConversation: ConversationDto) {
        const indexOfCachedConversation = this.conversationCache.findIndex((cachedConversation) => cachedConversation.id === updatedConversation.id);
        if (indexOfCachedConversation === -1) {
            console.error('Conversation with id ' + updatedConversation.id + " doesn't exist in cache, but was sent as 'UPDATE' action");
            this.conversationCache.push(updatedConversation);
        } else {
            this.conversationCache[indexOfCachedConversation] = updatedConversation;
        }
    }

    private handleReadConversation(readConversation: ConversationDto) {
        const indexOfCachedConversation = this.conversationCache.findIndex((cachedConversation) => cachedConversation.id === readConversation.id);
        if (indexOfCachedConversation === -1) {
            console.error('Conversation with id ' + readConversation.id + " doesn't exist in cache, but was sent as 'READ_CONVERSATION' action");
            this.conversationCache.push(readConversation);
        } else {
            this.conversationCache[indexOfCachedConversation] = readConversation;
        }

        // ToDo: Investigate how to handle the last read case now that we do not send the conversation particpants naymore
        // conversationDTO.conversation.conversationParticipants?.forEach((conversationParticipant) => {
        //     conversationParticipant.lastRead = conversationParticipant.lastRead ? dayjs(conversationParticipant.lastRead) : undefined;
        // });
    }

    /**
     * Via this Topic, users are informed about changes to which conversations they are a part of
     *
     * Users will be notified via this topic about the following events:
     * - GroupChats: When the creator of a group chat starts the conversation by sending the first message (group chat shows up when first message is sent)
     * - Channels: When the user is added to the channel (channel shows up when user is added)
     */
    private getConversationMembershipTopic(courseId: number, userId: number) {
        const courseTopicName = '/user' + MetisWebsocketChannelPrefix + 'courses/' + courseId;
        return courseTopicName + '/conversations/user/' + userId;
    }
}
