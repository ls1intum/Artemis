import { Injectable, OnDestroy } from '@angular/core';
import { ChatSession } from 'app/entities/metis/chat.session/chat-session.model';
import { map, Observable, ReplaySubject } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ChatSessionService } from 'app/shared/metis/chat-session.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ChatSessionDTO } from 'app/entities/metis/chat.session/chat-session-dto.model';
import { MetisPostAction, MetisWebsocketChannelPrefix } from 'app/shared/metis/metis.util';

@Injectable({ providedIn: 'root' })
export class CourseMessagesService implements OnDestroy {
    private chatSessions$: ReplaySubject<ChatSession[]> = new ReplaySubject<ChatSession[]>(1);
    private subscribedChannel?: string;
    userId: number;

    constructor(protected chatSessionService: ChatSessionService, private jhiWebsocketService: JhiWebsocketService, protected accountService: AccountService) {
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
     * creates a new chatSession by invoking the chatSession service
     * @param {number} courseId             course to associate the chatSession to
     * @param {ChatSession} chatSession     to be created
     * @return {Observable<ChatSession>}    created chatSession
     */
    createChatSession(courseId: number, chatSession: ChatSession): Observable<ChatSession> {
        return this.chatSessionService.create(courseId, chatSession).pipe(map((res: HttpResponse<ChatSession>) => res.body!));
    }

    get chatSessions(): Observable<ChatSession[]> {
        return this.chatSessions$.asObservable();
    }

    getChatSessionsOfUser(courseId: number): void {
        this.chatSessionService.getChatSessionsOfUser(courseId).subscribe((res) => {
            this.chatSessions$.next(res.body!);
            this.createSubscription(courseId, this.userId);
        });
    }

    private createSubscription(courseId: number, userId: number) {
        const channel = this.channelName(courseId, userId);

        this.jhiWebsocketService.unsubscribe(channel);
        this.subscribedChannel = channel;
        this.jhiWebsocketService.subscribe(channel);

        this.jhiWebsocketService.receive(channel).subscribe((chatSessionDTO: ChatSessionDTO) => {
            if (chatSessionDTO.crudAction === MetisPostAction.CREATE) {
                this.getChatSessionsOfUser(courseId);
            }
        });
    }

    private channelName(courseId: number, userId: number) {
        const courseTopicName = MetisWebsocketChannelPrefix + 'courses/' + courseId;
        const channel = courseTopicName + '/chatSessions/user/' + userId;
        return channel;
    }
}
