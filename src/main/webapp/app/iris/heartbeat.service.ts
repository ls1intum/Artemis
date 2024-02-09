import { Injectable, OnDestroy } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { HeartbeatDTO, IrisHttpChatSessionService } from 'app/iris/http-chat-session.service';
import { HttpResponse } from '@angular/common/http';
import { ConversationErrorOccurredAction, MessageStoreAction, RateLimitUpdatedAction, isSessionReceivedAction } from 'app/iris/state-store.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { Subscription, firstValueFrom } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

/**
 * The `IrisHeartbeatService` is responsible for monitoring the heartbeat of an Iris session.
 * It periodically sends HTTP requests to check if the session is still available.
 * If the heartbeat is not available, it dispatches an error action to the `IrisStateStore`.
 */
@Injectable()
export class IrisHeartbeatService implements OnDestroy {
    intervalId: ReturnType<typeof setInterval> | undefined;
    private sessionIdChangedSub: Subscription;
    websocketStatusSubscription: Subscription;
    disconnected = false;

    /**
     * Creates an instance of IrisHeartbeatService.
     * @param websocketService The JhiWebsocketService for managing the websocket connection.
     * @param stateStore The IrisStateStore for managing the state of the application.
     * @param httpSessionService The IrisHttpChatSessionService for HTTP operations related to sessions.
     */
    constructor(
        private websocketService: JhiWebsocketService,
        private stateStore: IrisStateStore,
        private httpSessionService: IrisHttpChatSessionService,
    ) {
        // Subscribe to changes in the session ID
        this.sessionIdChangedSub = this.stateStore.getActionObservable().subscribe((newAction: MessageStoreAction) => {
            if (!isSessionReceivedAction(newAction)) return;
            if (this.intervalId !== undefined) clearInterval(this.intervalId);
            this.checkHeartbeat(newAction.sessionId);
            this.intervalId = setInterval(() => {
                this.checkHeartbeat(newAction.sessionId);
            }, 10000);
        });
        this.websocketStatusSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.disconnected = !status.connected && !status.intendedDisconnect && status.wasEverConnectedBefore;
        });
    }

    /**
     * Checks the heartbeat of the Iris session by sending an HTTP request.
     * If the heartbeat is not available, it dispatches an error action to the `IrisStateStore`.
     * @param sessionId The ID of the Iris session to check.
     */
    private checkHeartbeat(sessionId: number): void {
        if (this.disconnected) return;
        firstValueFrom(this.httpSessionService.getHeartbeat(sessionId)).then((response: HttpResponse<HeartbeatDTO>) => {
            if (response.body) {
                if (!response.body.active) {
                    this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_NOT_AVAILABLE));
                }
                this.stateStore.dispatch(new RateLimitUpdatedAction(response.body!.rateLimitInfo));
            } else {
                this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_NOT_AVAILABLE));
            }
        });
    }

    /**
     * Performs cleanup when the service is destroyed.
     * Clears the interval and unsubscribes from observables.
     */
    ngOnDestroy(): void {
        if (this.intervalId !== undefined) clearInterval(this.intervalId);
        // Unsubscribe from observables
        this.sessionIdChangedSub.unsubscribe();
        this.websocketStatusSubscription.unsubscribe();
    }
}
