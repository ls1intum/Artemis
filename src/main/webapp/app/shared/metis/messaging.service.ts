import { Injectable, OnDestroy } from '@angular/core';
import { Observable, ReplaySubject, map } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ConversationService } from 'app/shared/metis/conversation.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation-dto.model';
import { MetisPostAction, MetisWebsocketChannelPrefix } from 'app/shared/metis/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import dayjs from 'dayjs/esm';

@Injectable()
export class MessagingService implements OnDestroy {
    private conversationsOfUser: Conversation[];
    private conversations$: ReplaySubject<Conversation[]> = new ReplaySubject<Conversation[]>(1);
    private conversation$ = new ReplaySubject<Conversation>(1);

    private subscribedChannel?: string;
    userId: number;

    constructor(protected conversationService: ConversationService, private jhiWebsocketService: JhiWebsocketService, protected accountService: AccountService) {
        this.accountService.identity().then((user: User) => {
            this.userId = user.id!;
        });
    }

    ngOnDestroy(): void {
        if (this.subscribedChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscribedChannel);
        }
    }

    /**
     * creates a new conversation by invoking the conversation service
     * @param {number} courseId             course to associate the conversation to
     * @param {Conversation} conversation    to be created
     * @return {Observable<Conversation>}    created conversation
     */
    createConversation(courseId: number, conversation: Conversation): Observable<Conversation> {
        this.conversationService
            .create(courseId, conversation)
            .pipe(map((res: HttpResponse<Conversation>) => res.body!))
            .subscribe({
                next: (receivedConversation: Conversation) => {
                    this.conversationsOfUser.unshift(receivedConversation);
                    this.conversations$.next(this.conversationsOfUser);
                    this.conversation$.next(receivedConversation);
                },
            });

        return this.conversation$;
    }

    get conversations(): Observable<Conversation[]> {
        return this.conversations$.asObservable();
    }

    getConversationsOfUser(courseId: number): void {
        this.conversationService.getConversationsOfUser(courseId).subscribe((res) => {
            this.conversationsOfUser = res.body!;
            this.conversations$.next(this.conversationsOfUser);
            this.createWebSocketSubscription(courseId, this.userId);
        });
    }

    createWebSocketSubscription(courseId: number, userId: number) {
        const channel = this.channelName(courseId, userId);

        this.jhiWebsocketService.unsubscribe(channel);
        this.subscribedChannel = channel;
        this.jhiWebsocketService.subscribe(channel);

        this.jhiWebsocketService.receive(channel).subscribe((conversationDTO: ConversationDTO) => {
            conversationDTO.conversation.creationDate = conversationDTO.conversation.creationDate ? dayjs(conversationDTO.conversation.creationDate) : undefined;
            conversationDTO.conversation.lastMessageDate = conversationDTO.conversation.lastMessageDate ? dayjs(conversationDTO.conversation.lastMessageDate) : undefined;
            conversationDTO.conversation.conversationParticipants?.forEach((conversationParticipant) => {
                conversationParticipant.lastRead = conversationParticipant.lastRead ? dayjs(conversationParticipant.lastRead) : undefined;
            });

            const conversationIndexInCache = this.conversationsOfUser.findIndex((conversation) => conversation.id === conversationDTO.conversation.id);
            if (conversationDTO.crudAction === MetisPostAction.CREATE || conversationDTO.crudAction === MetisPostAction.UPDATE) {
                if (conversationIndexInCache !== -1) {
                    this.conversationsOfUser.splice(conversationIndexInCache, 1);
                }

                // add created/updated conversation to the beginning of the conversation list
                this.conversationsOfUser.unshift(conversationDTO.conversation);
            } else if (conversationDTO.crudAction === MetisPostAction.READ_CONVERSATION) {
                // conversation is updated without being moved to the beginning of the list
                this.conversationsOfUser[conversationIndexInCache] = conversationDTO.conversation;
            }

            this.conversations$.next(this.conversationsOfUser);
        });
    }

    private channelName(courseId: number, userId: number) {
        const courseTopicName = '/user' + MetisWebsocketChannelPrefix + 'courses/' + courseId;
        const channel = courseTopicName + '/conversations/user/' + userId;
        return channel;
    }
}
