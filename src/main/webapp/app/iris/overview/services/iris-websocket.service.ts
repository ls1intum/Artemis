import { Injectable, OnDestroy, inject } from '@angular/core';
import { IrisChatWebsocketDTO } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisCommandRequestDTO } from 'app/iris/shared/entities/iris-command-request-dto.model';
import { IrisCommandAckDTO } from 'app/iris/shared/entities/iris-command-ack-dto.model';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';

type SubscribedChannel<T> = { wsSubscription: Subscription; subject: Subject<T> };

/** STOMP destination the client publishes command acknowledgements to (handled server-side by IrisCommandWebsocketController). */
const COMMAND_ACK_DESTINATION = '/topic/iris/command-ack';

/**
 * The IrisWebsocketService handles the websocket communication for receiving messages in dedicated channels.
 */
@Injectable({ providedIn: 'root' })
export class IrisWebsocketService implements OnDestroy {
    protected websocketService = inject(WebsocketService);

    private subscribedChannels: Map<number, SubscribedChannel<IrisChatWebsocketDTO>> = new Map();

    private commandChannels: Map<number, SubscribedChannel<IrisCommandRequestDTO>> = new Map();

    /**
     * Cleans up resources before the service is destroyed.
     */
    ngOnDestroy(): void {
        this.subscribedChannels.forEach((subscription, _sessionId) => {
            subscription.wsSubscription.unsubscribe();
        });
        this.commandChannels.forEach((subscription, _sessionId) => {
            subscription.wsSubscription.unsubscribe();
        });
    }

    /**
     * Subscribes to a session.
     * @param sessionId The session ID to subscribe to.
     */
    public subscribeToSession(sessionId: number): Observable<IrisChatWebsocketDTO> {
        if (!sessionId) {
            throw new Error('Session ID is required');
        }

        const subscribedChannel = this.subscribedChannels.computeIfAbsent(sessionId, () => {
            const channel = this.getChannelFromSessionId(sessionId);
            const subject = new Subject<IrisChatWebsocketDTO>();
            const wsSubscription = this.websocketService.subscribe<IrisChatWebsocketDTO>(channel).subscribe((response: IrisChatWebsocketDTO) => {
                subject.next(response);
            });
            return { wsSubscription, subject };
        });

        return subscribedChannel.subject.asObservable();
    }

    /**
     * Subscribes to the command-request channel of a session. The server pushes commands here (e.g. a point-out) while
     * the pipeline is still running, expecting the client to carry them out and acknowledge via {@link sendCommandAck}.
     * @param sessionId The session ID to subscribe to.
     */
    public subscribeToSessionCommands(sessionId: number): Observable<IrisCommandRequestDTO> {
        if (!sessionId) {
            throw new Error('Session ID is required');
        }

        const subscribedChannel = this.commandChannels.computeIfAbsent(sessionId, () => {
            const channel = this.getChannelFromSessionId(sessionId) + '/commands';
            const subject = new Subject<IrisCommandRequestDTO>();
            const wsSubscription = this.websocketService.subscribe<IrisCommandRequestDTO>(channel).subscribe((response: IrisCommandRequestDTO) => {
                subject.next(response);
            });
            return { wsSubscription, subject };
        });

        return subscribedChannel.subject.asObservable();
    }

    /**
     * Publishes a command acknowledgement back to the server, unblocking the pipeline waiting on it.
     * @param ack the acknowledgement carrying the correlation id and whether the command was carried out
     */
    public sendCommandAck(ack: IrisCommandAckDTO): void {
        this.websocketService.send(COMMAND_ACK_DESTINATION, ack);
    }

    /**
     * Unsubscribes from a session.
     * @param sessionId The session ID to unsubscribe from.
     * @return true if the session was successfully unsubscribed, false otherwise.
     */
    public unsubscribeFromSession(sessionId: number): boolean {
        const commandChannel = this.commandChannels.get(sessionId);
        if (commandChannel) {
            commandChannel.wsSubscription.unsubscribe();
            this.commandChannels.delete(sessionId);
        }
        const subscribedChannel = this.subscribedChannels.get(sessionId);
        if (subscribedChannel) {
            subscribedChannel.wsSubscription.unsubscribe();
            this.subscribedChannels.delete(sessionId);
            return true;
        }
        return false;
    }

    private getChannelFromSessionId(sessionId: number) {
        return '/user/topic/iris/' + sessionId;
    }
}
