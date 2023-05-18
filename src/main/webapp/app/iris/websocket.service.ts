import { Subscription } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Injectable, OnDestroy } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { ActiveConversationMessageLoadedAction, MessageStoreAction, isSessionIdReceivedAction } from 'app/iris/message-store.model';
import { IrisMessage, IrisSender } from 'app/entities/iris/iris.model';

@Injectable()
export class IrisWebsocketService implements OnDestroy {
    private user: User;
    private subscriptionChannel?: string;
    private sessionIdChangedSub: Subscription;

    constructor(protected accountService: AccountService, private jhiWebsocketService: JhiWebsocketService, private messageStore: IrisMessageStore) {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });
        this.sessionIdChangedSub = this.messageStore.getActionObservable().subscribe((newAction: MessageStoreAction) => {
            if (!isSessionIdReceivedAction(newAction)) return;
            this.changeWebsocketSubscription(newAction.sessionId);
        });
    }

    ngOnDestroy(): void {
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
        this.sessionIdChangedSub.unsubscribe();
    }

    /**
     * Creates (and updates) the websocket channel for receiving messages in dedicated channels;
     * @param sessionId which the iris service should subscribe to
     */
    private changeWebsocketSubscription(sessionId: number | null): void {
        const channel = 'topic/iris/sessions/' + sessionId;

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
        this.jhiWebsocketService.receive(this.subscriptionChannel).subscribe((newMessage: IrisMessage) => {
            if (newMessage.sender === IrisSender.USER) return;
            this.messageStore.dispatch(new ActiveConversationMessageLoadedAction(newMessage));
        });
    }
}
