import { Subscription } from 'rxjs';
import { Injectable, OnDestroy } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import {
    ActiveConversationMessageLoadedAction,
    ConversationErrorOccurredAction,
    MessageStoreAction,
    RateLimitUpdatedAction,
    StudentMessageSentAction,
    isSessionReceivedAction,
} from 'app/iris/state-store.model';
import { IrisMessage, isServerSentMessage, isStudentSentMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';

export class IrisRateLimitInformation {
    constructor(
        public currentMessageCount: number,
        public rateLimit: number,
        public rateLimitTimeframeHours: number,
    ) {}
}

/**
 * The IrisWebsocketService handles the websocket communication for receiving messages in dedicated channels.
 */
@Injectable()
export abstract class IrisWebsocketService implements OnDestroy {
    private subscriptionChannel?: string;
    private sessionIdChangedSub: Subscription;

    /**
     * Creates an instance of IrisWebsocketService.
     * @param jhiWebsocketService The JhiWebsocketService for websocket communication.
     * @param stateStore The IrisStateStore for managing the state of the application.
     * @param sessionType The session type of the websocket subscription channel
     */
    protected constructor(
        protected jhiWebsocketService: JhiWebsocketService,
        protected stateStore: IrisStateStore,
        protected sessionType: string,
    ) {
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
        const channel = '/user/topic/iris/' + this.sessionType + '/' + sessionId;

        if (sessionId != null && this.subscriptionChannel === channel) {
            return;
        }

        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }

        if (sessionId == null) return;

        this.subscriptionChannel = channel;
        this.jhiWebsocketService.subscribe(this.subscriptionChannel);
        this.jhiWebsocketService.receive(this.subscriptionChannel).subscribe((response: any) => {
            this.handleWebsocketResponse(response);
        });
    }

    protected abstract handleWebsocketResponse(response: any): void;

    protected handleMessage(message?: IrisMessage) {
        if (!message) return;
        if (isStudentSentMessage(message)) {
            this.stateStore.dispatch(
                new StudentMessageSentAction(
                    message,
                    setTimeout(() => {
                        // will be cleared by the store automatically
                        this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT));
                    }, 20000),
                ),
            );
        } else if (isServerSentMessage(message)) {
            this.stateStore.dispatch(new ActiveConversationMessageLoadedAction(message));
        }
    }

    protected handleError(errorTranslationKey?: IrisErrorMessageKey, translationParams?: Map<string, any>) {
        if (!errorTranslationKey) {
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE));
        } else {
            this.stateStore.dispatch(new ConversationErrorOccurredAction(errorTranslationKey, translationParams));
        }
    }

    protected handleRateLimitInfo(rateLimitInfo: IrisRateLimitInformation) {
        this.stateStore.dispatch(new RateLimitUpdatedAction(rateLimitInfo));
    }
}
