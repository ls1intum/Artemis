import { IrisMessage } from 'app/entities/iris/message.model';
import { MessageService } from 'app/shared/iris/message.service';
import { BehaviorSubject, Observable, ReplaySubject, map } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Injectable, OnDestroy } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Injectable()
export class IrisService implements OnDestroy {
    private messages$: ReplaySubject<IrisMessage[]> = new ReplaySubject<IrisMessage[]>(1);
    private user: User;
    private sessionId: number;
    private subscriptionChannel?: string;

    constructor(protected messageService: MessageService, protected accountService: AccountService, private jhiWebsocketService: JhiWebsocketService) {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });
    }

    get messages(): Observable<IrisMessage[]> {
        return this.messages$.asObservable();
    }
    ngOnDestroy(): void {
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
    }

    getUser(): User {
        return this.user;
    }
    /**
     * to be used to set posts from outside
     * @param {IrisMessage[]} messages that are managed by metis service
     */
    setIrisMessages(messages: IrisMessage[]): void {
        this.messages$.next(messages);
    }
    /**
     * creates a new message by invoking the message service
     * @param {IrisMessage} message to be created
     * @return {Observable<IrisMessage>} created post
     */
    createIrisMessage(message: IrisMessage): Observable<IrisMessage> {
        return this.messageService.create(this.sessionId, message).pipe(map((res: HttpResponse<IrisMessage>) => res.body!));
    }
    /**
     * Creates (and updates) the websocket channel for receiving messages in dedicated channels;
     * @param channel which the metis service should subscribe to
     */
    createWebsocketSubscription(channel: string): void {
        // if channel subscription does not change, do nothing
        if (this.subscriptionChannel === channel) {
            return;
        }
        // unsubscribe from existing channel subscription
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
        // create new subscription
        this.subscriptionChannel = channel;
        this.jhiWebsocketService.subscribe(this.subscriptionChannel);
        this.jhiWebsocketService.receive(this.subscriptionChannel).subscribe();
    }

    /**
     * Determines the channel to be used for websocket communication based on the current post context filter,
     * i.e., when being on a lecture page, the context is a certain lectureId (e.g., 1), the channel is set to '/topic/metis/lectures/1';
     * By calling the createWebsocketSubscription method with this channel as parameter, the metis service also subscribes to that messages in this channel
     */
    private createSubscriptionChannel(): void {
        let channel = 'topic/iris/sessions';
        channel += this.sessionId;
        this.createWebsocketSubscription(channel);
    }
}
