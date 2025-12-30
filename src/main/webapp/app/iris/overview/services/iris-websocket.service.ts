import { Injectable, OnDestroy, inject } from '@angular/core';
import { IrisChatWebsocketDTO } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';

type SubscribedChannel<T> = { wsSubscription: Subscription; subject: Subject<T> };

/**
 * The IrisWebsocketService handles the websocket communication for receiving messages in dedicated channels.
 */
@Injectable({ providedIn: 'root' })
export class IrisWebsocketService implements OnDestroy {
    protected websocketService = inject(WebsocketService);

    private subscribedChannels: Map<number, SubscribedChannel<IrisChatWebsocketDTO>> = new Map();

    /**
     * Cleans up resources before the service is destroyed.
     */
    ngOnDestroy(): void {
        this.subscribedChannels.forEach((subscription, _sessionId) => {
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
            const subject = new Subject<any>();
            const wsSubscription = this.websocketService.subscribe(channel).subscribe((response: any) => {
                subject.next(response);
            });
            return { wsSubscription, subject };
        });

        return subscribedChannel.subject.asObservable();
    }

    /**
     * Unsubscribes from a session.
     * @param sessionId The session ID to unsubscribe from.
     * @return true if the session was successfully unsubscribed, false otherwise.
     */
    public unsubscribeFromSession(sessionId: number): boolean {
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
