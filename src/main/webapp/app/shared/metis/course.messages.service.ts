import { Injectable, OnDestroy } from '@angular/core';
import { map, Observable, ReplaySubject } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ConversationService } from 'app/shared/metis/conversation.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation-dto.model';
import { MetisPostAction, MetisWebsocketChannelPrefix } from 'app/shared/metis/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

@Injectable()
export class CourseMessagesService implements OnDestroy {
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
            this.createSubscription(courseId, this.userId);
        });
    }

    private createSubscription(courseId: number, userId: number) {
        const channel = this.channelName(courseId, userId);

        this.jhiWebsocketService.unsubscribe(channel);
        this.subscribedChannel = channel;
        this.jhiWebsocketService.subscribe(channel);

        this.jhiWebsocketService.receive(channel).subscribe((conversationDTO: ConversationDTO) => {
            if (conversationDTO.crudAction === MetisPostAction.CREATE || conversationDTO.crudAction === MetisPostAction.UPDATE) {
                if (conversationDTO.crudAction === MetisPostAction.UPDATE) {
                    this.conversationsOfUser.splice(
                        this.conversationsOfUser.findIndex((conversation) => conversation.id === conversationDTO.conversation.id),
                        1,
                    );
                    this.conversationService.auditConversationReadTimeOfUser(conversationDTO.conversation.id!);
                }

                // add created/updated conversation to the beginning of the conversation list
                this.conversationsOfUser.unshift(conversationDTO.conversation);
            } else if (conversationDTO.crudAction === MetisPostAction.READ_CONVERSATION) {
                this.conversationsOfUser[this.conversationsOfUser.findIndex((conversation) => conversation.id === conversationDTO.conversation.id)] = conversationDTO.conversation;
                this.conversations$.next(this.conversationsOfUser);
            }

            this.conversations$.next(this.conversationsOfUser);
        });
    }

    private channelName(courseId: number, userId: number) {
        const courseTopicName = MetisWebsocketChannelPrefix + 'courses/' + courseId;
        const channel = courseTopicName + '/conversations/user/' + userId;
        return channel;
    }
}
