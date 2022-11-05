import { Injectable, OnDestroy } from '@angular/core';
import { map, Observable, ReplaySubject } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ConversationWebsocketDTO } from 'app/entities/metis/conversation/conversation-websocket-dto.model';
import { MetisPostAction, MetisWebsocketChannelPrefix } from 'app/shared/metis/metis.util';
import { Conversation, ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { tap } from 'rxjs/operators';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { isGroupChat, isGroupChatDto } from 'app/entities/metis/conversation/groupChat.model';
import { isChannel } from 'app/entities/metis/conversation/channel.model';

/**
 * NOTE: NOT INJECTED IN THE ROOT MODULE
 */
@Injectable()
export class MessagingService implements OnDestroy {
    private conversationsOfUser: ConversationDto[];
    private conversations$: ReplaySubject<ConversationDto[]> = new ReplaySubject<ConversationDto[]>(1);
    private conversation$ = new ReplaySubject<ConversationDto>(1);

    private subscribedWebsocketChannel?: string;
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
        if (this.subscribedWebsocketChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscribedWebsocketChannel);
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
                this.conversationsOfUser.unshift(receivedConversation);
                this.conversations$.next(this.conversationsOfUser);
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

        return this.conversation$;
    }

    get conversations(): Observable<ConversationDto[]> {
        return this.conversations$.asObservable();
    }

    getConversationsOfUser(courseId: number): Observable<ConversationDto[]> {
        return this.conversationService.getConversationsOfUser(courseId).pipe(
            map((res: HttpResponse<ConversationDto[]>) => res.body ?? []),
            tap((conversation) => {
                this.conversationsOfUser = conversation;
                this.conversations$.next(this.conversationsOfUser);
                this.createWebSocketSubscription(courseId, this.userId);
            }),
        );
    }

    createWebSocketSubscription(courseId: number, userId: number) {
        const channel = this.channelName(courseId, userId);

        this.jhiWebsocketService.unsubscribe(channel);
        this.subscribedWebsocketChannel = channel;
        this.jhiWebsocketService.subscribe(channel);

        this.jhiWebsocketService.receive(channel).subscribe((conversationDTO: ConversationWebsocketDTO) => {
            conversationDTO.conversation.creationDate = conversationDTO.conversation.creationDate ? dayjs(conversationDTO.conversation.creationDate) : undefined;
            conversationDTO.conversation.lastMessageDate = conversationDTO.conversation.lastMessageDate ? dayjs(conversationDTO.conversation.lastMessageDate) : undefined;

            // ToDo: Investigate how to handle the last read case now
            // conversationDTO.conversation.conversationParticipants?.forEach((conversationParticipant) => {
            //     conversationParticipant.lastRead = conversationParticipant.lastRead ? dayjs(conversationParticipant.lastRead) : undefined;
            // });

            const conversationIndexInCache = this.conversationsOfUser.findIndex((conversation) => conversation.id === conversationDTO.conversation.id);
            if (conversationDTO.crudAction === MetisPostAction.CREATE || conversationDTO.crudAction === MetisPostAction.UPDATE) {
                // add created/updated direct conversation to the beginning of the conversation list
                if (isGroupChatDto(conversationDTO.conversation)) {
                    if (conversationIndexInCache !== -1) {
                        this.conversationsOfUser.splice(conversationIndexInCache, 1);
                    }
                    this.conversationsOfUser.unshift(conversationDTO.conversation);
                }
            } else if (conversationDTO.crudAction === MetisPostAction.READ_CONVERSATION) {
                // conversation is updated without being moved to the beginning of the list
                this.conversationsOfUser[conversationIndexInCache] = conversationDTO.conversation;
            }
            this.conversations$.next(this.conversationsOfUser);
        });
    }

    private channelName(courseId: number, userId: number) {
        const courseTopicName = '/user' + MetisWebsocketChannelPrefix + 'courses/' + courseId;
        return courseTopicName + '/conversations/user/' + userId;
    }
}
