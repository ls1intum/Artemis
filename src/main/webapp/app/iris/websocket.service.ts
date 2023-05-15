import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { Observable, Subscription } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Injectable, OnDestroy } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { ActiveConversationMessageLoadedAction, MessageStoreState } from 'app/iris/message-store.model';
import { IrisMessageDescriptor, IrisSender } from 'app/entities/iris/iris.model';

@Injectable()
export class IrisWebsocketService implements OnDestroy {
    private user: User;
    private sessionId: number;
    private subscriptionChannel?: string;
    private connectionStateSub: Subscription;

    constructor(
        protected messageService: IrisHttpMessageService,
        protected accountService: AccountService,
        private jhiWebsocketService: JhiWebsocketService,
        private messageStore: IrisMessageStore,
    ) {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });
        this.connectionStateSub = this.jhiWebsocketService.connectionState.subscribe((newState) => {
            //TODO
        });
    }

    get messages(): Observable<MessageStoreState> {
        return this.messageStore.getState();
    }

    ngOnDestroy(): void {
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
        this.connectionStateSub.unsubscribe();
    }

    getUser(): User {
        return this.user;
    }

    /**
     * Creates (and updates) the websocket channel for receiving messages in dedicated channels;
     * @param channel which the iris service should subscribe to
     */
    changeWebsocketSubscription(channel: string | null): void {
        // if channel subscription does not change, do nothing
        if (this.subscriptionChannel === channel) {
            return;
        }
        // unsubscribe from existing channel subscription
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
        if (channel == null) return;
        // create new subscription
        this.subscriptionChannel = channel;
        this.jhiWebsocketService.subscribe(this.subscriptionChannel);
        this.jhiWebsocketService.receive(this.subscriptionChannel).subscribe((newMessage: IrisMessageDescriptor) => {
            if (newMessage.sender === IrisSender.USER) return;
            this.messageStore.dispatch(new ActiveConversationMessageLoadedAction(newMessage));
        });
    }

    /**
     * Determines the channel to be used for websocket communication
     * By calling the createWebsocketSubscription method with this channel as parameter, the iris service also subscribes to that messages in this channel
     */
    private createSubscriptionChannel(): void {
        const channel = 'topic/iris/sessions/' + this.sessionId;
        this.changeWebsocketSubscription(channel);
    }
}
