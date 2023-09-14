import { Subscription } from 'rxjs';
import { Injectable, OnDestroy } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import {
    ActiveConversationMessageLoadedAction,
    ConversationErrorOccurredAction,
    MessageStoreAction,
    StudentMessageSentAction,
    isSessionReceivedAction,
} from 'app/iris/state-store.model';
import { IrisMessage, isServerSentMessage, isStudentSentMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';

/**
 * The IrisWebsocketMessageType defines the type of message sent over the websocket.
 */
export enum IrisWebsocketMessageType {
    MESSAGE = 'MESSAGE',
    ERROR = 'ERROR',
}

/**
 * The IrisWebsocketDTO is the data transfer object for messages sent over the websocket.
 * It either contains an IrisMessage or an error message.
 */
export class IrisWebsocketDTO {
    type: IrisWebsocketMessageType;
    message?: IrisMessage;
    errorTranslationKey?: IrisErrorMessageKey;
    translationParams?: Map<string, any>;
}

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
    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private stateStore: IrisStateStore,
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
        const channel = '/user/topic/iris/sessions/' + sessionId;

        if (sessionId != null && this.subscriptionChannel === channel) {
            return;
        }

        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }

        if (sessionId == null) return;

        this.subscriptionChannel = channel;
        this.jhiWebsocketService.subscribe(this.subscriptionChannel);
        this.jhiWebsocketService.receive(this.subscriptionChannel).subscribe((websocketResponse: IrisWebsocketDTO) => {
            if (websocketResponse.type === IrisWebsocketMessageType.ERROR) {
                if (!websocketResponse.errorTranslationKey) {
                    this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE));
                } else {
                    this.stateStore.dispatch(new ConversationErrorOccurredAction(websocketResponse.errorTranslationKey, websocketResponse.translationParams));
                }
            } else if (websocketResponse.type === IrisWebsocketMessageType.MESSAGE) {
                const message = websocketResponse.message;
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
        });
    }
}
