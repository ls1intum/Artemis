import { Subscription } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Injectable, OnDestroy } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActiveConversationMessageLoadedAction, MessageStoreAction, isSessionReceivedAction } from 'app/iris/message-store.model';
import { IrisServerMessage } from 'app/entities/iris/iris-message.model';

@Injectable()
export class IrisWebsocketService implements OnDestroy {
    private user: User;
    private subscriptionChannel?: string;
    private sessionIdChangedSub: Subscription;
    private connectionStateSub: Subscription;

    constructor(protected accountService: AccountService, private jhiWebsocketService: JhiWebsocketService, private stateStore: IrisStateStore) {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });
        // this.connectionStateSub = this.jhiWebsocketService.connectionState.subscribe((newState) => {
        //     if (newState.connected == false) {
        //         this.messageStore.dispatch(new ConversationErrorOccurred('The connection to Artemis Server has been lost. Please, reload the page.')); // TODO rp + i18n
        //     }
        // });
        this.sessionIdChangedSub = this.stateStore.getActionObservable().subscribe((newAction: MessageStoreAction) => {
            if (!isSessionReceivedAction(newAction)) return;
            this.changeWebsocketSubscription(newAction.sessionId);
        });
    }

    ngOnDestroy(): void {
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
        this.sessionIdChangedSub.unsubscribe();
        // this.connectionStateSub.unsubscribe();
    }

    /**
     * Creates (and updates) the websocket channel for receiving messages in dedicated channels;
     * @param sessionId which the iris service should subscribe to
     */
    private changeWebsocketSubscription(sessionId: number | null): void {
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
            this.stateStore.dispatch(new ActiveConversationMessageLoadedAction(newMessage));
        });
    }
}
