import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { IrisMessageService } from 'app/shared/iris/iris-message.service';
import { Observable, ReplaySubject, map } from 'rxjs';
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

    constructor(protected messageService: IrisMessageService, protected accountService: AccountService, private jhiWebsocketService: JhiWebsocketService) {
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
     * creates a new message by invoking the message service
     * @param {IrisMessage} message to be created
     * @return {Observable<IrisMessage>} created message
     */
    createIrisMessage(message: IrisMessage): Observable<IrisMessage> {
        return this.messageService.createMessage(this.sessionId, message).pipe(map((res: HttpResponse<IrisMessage>) => res.body!));
    }
    /**
     * Creates (and updates) the websocket channel for receiving messages in dedicated channels;
     * @param channel which the iris service should subscribe to
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
     * Determines the channel to be used for websocket communication
     * By calling the createWebsocketSubscription method with this channel as parameter, the iris service also subscribes to that messages in this channel
     */
    private createSubscriptionChannel(): void {
        const channel = 'topic/iris/sessions/' + this.sessionId;
        this.createWebsocketSubscription(channel);
    }
}
