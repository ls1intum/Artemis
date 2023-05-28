import { Subscription } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Injectable, OnDestroy } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { ActiveConversationMessageLoadedAction } from 'app/iris/message-store.model';
import { IrisServerMessage } from 'app/entities/iris/iris.model';

@Injectable()
export class IrisWebsocketService implements OnDestroy {
    private user: User;
    private subscriptionChannel?: string;
    private sessionIdChangedSub: Subscription;
    private messageStore: IrisMessageStore;
    private callbacks: () => void = () => {};

    constructor(protected accountService: AccountService, private jhiWebsocketService: JhiWebsocketService) {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });
    }

    ngOnDestroy(): void {
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
        this.sessionIdChangedSub.unsubscribe();
    }

    setMessageStore(messageStore: IrisMessageStore): void {
        this.messageStore = messageStore;
        this.messageStore.getState().subscribe((state) => {
            this.subscribeToWebsocket(state.sessionId);
        });
    }

    setCallbacks(callbacks: () => void): void {
        this.callbacks = callbacks;
    }

    /**
     * Creates (and updates) the websocket channel for receiving messages in dedicated channels;
     * @param sessionId which the iris service should subscribe to
     */
    private subscribeToWebsocket(sessionId: number | null): void {
        const channel = '/user/topic/iris/sessions/' + sessionId;

        // if channel subscription does not change, do nothing
        if (sessionId != null && this.subscriptionChannel === channel) {
            return;
        }
        // unsubscribe from existing channel subscription
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
        if (sessionId == null) return;
        // create new subscription
        this.subscriptionChannel = channel;
        this.jhiWebsocketService.subscribe(this.subscriptionChannel);
        this.jhiWebsocketService.receive(this.subscriptionChannel).subscribe((newMessage: IrisServerMessage) => {
            this.messageStore.dispatch(new ActiveConversationMessageLoadedAction(newMessage));
            this.callbacks();
        });
    }
}
