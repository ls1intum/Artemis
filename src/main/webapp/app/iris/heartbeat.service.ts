import { Injectable, OnDestroy } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisHttpSessionService } from 'app/iris/http-session.service';
import { HttpResponse } from '@angular/common/http';
import { ConversationErrorOccurredAction, MessageStoreAction, isSessionReceivedAction } from 'app/iris/state-store.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';

/**
 * The IrisWebsocketService handles the websocket communication for receiving messages in dedicated channels.
 */
@Injectable()
export class IrisHeartbeatService implements OnDestroy {
    intervalId: ReturnType<typeof setInterval>;
    private sessionIdChangedSub: Subscription;

    /**
     * Creates an instance of IrisWebsocketService.
     * @param httpSessionService The IrisHttpSessionService for HTTP operations related to sessions.
     * @param stateStore The IrisStateStore for managing the state of the application.
     */
    constructor(private stateStore: IrisStateStore, private httpSessionService: IrisHttpSessionService) {
        // Subscribe to changes in the session ID
        this.sessionIdChangedSub = this.stateStore.getActionObservable().subscribe((newAction: MessageStoreAction) => {
            if (!isSessionReceivedAction(newAction)) return;
            clearInterval(this.intervalId);
            this.checkHeartbeat(newAction.sessionId);
            this.intervalId = setInterval(() => {
                this.checkHeartbeat(newAction.sessionId);
            }, 10000);
        });
    }

    private checkHeartbeat(sessionId: number) {
        this.httpSessionService
            .getHeartbeat(sessionId)
            .toPromise()
            .then((response: HttpResponse<boolean>) => {
                if (!response.body!) {
                    this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_NOT_AVAILABLE));
                }
            });
    }

    ngOnDestroy(): void {
        clearInterval(this.intervalId);
        // Unsubscribe from observables
        this.sessionIdChangedSub.unsubscribe();
    }
}
