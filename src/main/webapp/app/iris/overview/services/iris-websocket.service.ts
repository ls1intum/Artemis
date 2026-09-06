import { Injectable, OnDestroy, inject } from '@angular/core';
import { IrisChatWebsocketDTO } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisCommand, IrisCommandAckDTO } from 'app/iris/shared/entities/iris-command.model';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { generateUuid } from 'app/foundation/util/crypto.utils';
import { Observable, Subject, Subscription } from 'rxjs';

type SubscribedChannel<T> = { wsSubscription: Subscription; subject: Subject<T> };

/** STOMP destination the client publishes command acknowledgements to (handled server-side by IrisCommandWebsocketController). */
const COMMAND_ACK_DESTINATION = '/topic/iris/command-ack';

/** Suffix appended to the per-session Iris topic for command requests. Mirrors the server's IrisCommandService.COMMAND_TOPIC_SUFFIX. */
const COMMAND_TOPIC_SUFFIX = '/commands';

/**
 * The IrisWebsocketService handles the websocket communication for receiving messages in dedicated channels.
 */
@Injectable({ providedIn: 'root' })
export class IrisWebsocketService implements OnDestroy {
    protected websocketService = inject(WebsocketService);

    /**
     * Identifies this browser tab for the lifetime of the page. Sent along with each user message so that a command
     * the server pushes while answering can name the tab that is meant, and compared against incoming commands so the
     * other tabs of the same user ignore them. A reload produces a new id, which is the correct outcome: the tab that
     * would have carried out a command in flight no longer exists.
     */
    readonly clientId = generateUuid();

    private subscribedChannels: Map<number, SubscribedChannel<IrisChatWebsocketDTO>> = new Map();

    private commandChannels: Map<number, SubscribedChannel<IrisCommand>> = new Map();

    /**
     * Cleans up resources before the service is destroyed.
     */
    ngOnDestroy(): void {
        [...this.subscribedChannels.values(), ...this.commandChannels.values()].forEach((channel) => channel.wsSubscription.unsubscribe());
    }

    /**
     * Subscribes to a session.
     * @param sessionId The session ID to subscribe to.
     */
    public subscribeToSession(sessionId: number): Observable<IrisChatWebsocketDTO> {
        return this.subscribeToChannel(this.subscribedChannels, sessionId, '');
    }

    /**
     * Subscribes to the command-request channel of a session. The server pushes commands here (e.g. a point-out) while
     * the pipeline is still running, expecting the client to carry them out and acknowledge via {@link sendCommandAck}.
     * @param sessionId The session ID to subscribe to.
     */
    public subscribeToSessionCommands(sessionId: number): Observable<IrisCommand> {
        return this.subscribeToChannel(this.commandChannels, sessionId, COMMAND_TOPIC_SUFFIX);
    }

    /**
     * Publishes a command acknowledgement back to the server, unblocking the Iris pipeline that is waiting on it.
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

    /**
     * Subscribes to one of a session's topics, reusing the existing subscription if there already is one.
     * @param channels    the per-session bookkeeping map for this topic
     * @param sessionId   the session ID to subscribe to
     * @param topicSuffix suffix appended to the session topic ('' for the main message channel)
     */
    private subscribeToChannel<T>(channels: Map<number, SubscribedChannel<T>>, sessionId: number, topicSuffix: string): Observable<T> {
        if (!sessionId) {
            throw new Error('Session ID is required');
        }
        const channel = channels.computeIfAbsent(sessionId, () => {
            const subject = new Subject<T>();
            const wsSubscription = this.websocketService.subscribe<T>('/user/topic/iris/' + sessionId + topicSuffix).subscribe((response: T) => subject.next(response));
            return { wsSubscription, subject };
        });
        return channel.subject.asObservable();
    }
}
