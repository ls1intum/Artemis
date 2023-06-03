import { Subscription } from 'rxjs';
import { Injectable, OnDestroy } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActiveConversationMessageLoadedAction, MessageStoreAction, isSessionReceivedAction } from 'app/iris/state-store.model';
import { IrisServerMessage } from 'app/entities/iris/iris-message.model';

/**
 * The IrisWebsocketService handles the websocket communication for receiving messages in dedicated channels.
 */
@Injectable()
export class IrisWebsocketService implements OnDestroy {
    private subscriptionChannel?: string;
    private sessionIdChangedSub: Subscription;

    /**
     * Creates an instance of IrisWebsocketService.
     * @param jhiWebsocketService The JhiWebsocketService for websocket communication.
     * @param stateStore The IrisStateStore for managing the state of the application.
     */
    constructor(private jhiWebsocketService: JhiWebsocketService, private stateStore: IrisStateStore) {
        // Subscribe to changes in the session ID
        this.sessionIdChangedSub = this.stateStore.getActionObservable().subscribe((newAction: MessageStoreAction) => {
            if (!isSessionReceivedAction(newAction)) return;
            this.changeWebsocketSubscription(newAction.sessionId);
        });
    }

    /**
     * Cleans up resources before the service is destroyed.
     */
    ngOnDestroy(): void {
        // Unsubscribe from the websocket channel
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
        // Unsubscribe from observables
        this.sessionIdChangedSub.unsubscribe();
    }

    /**
     * Changes the websocket subscription to the specified session ID.
     * @param sessionId The session ID to subscribe to.
     */
    private changeWebsocketSubscription(sessionId: number | null): void {
        const channel = '/user/topic/iris/sessions/' + sessionId;

        // If the channel subscription does not change, do nothing
        if (sessionId != null && this.subscriptionChannel === channel) {
            return;
        }

        // Unsubscribe from the existing channel subscription
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }

        if (sessionId == null) return;

        // Create a new subscription
        this.subscriptionChannel = channel;
        this.jhiWebsocketService.subscribe(this.subscriptionChannel);
        this.jhiWebsocketService.receive(this.subscriptionChannel).subscribe((newMessage: IrisServerMessage) => {
            // Dispatch a new action to update the state with the received message
            this.stateStore.dispatch(new ActiveConversationMessageLoadedAction(newMessage));
        });
    }
}
