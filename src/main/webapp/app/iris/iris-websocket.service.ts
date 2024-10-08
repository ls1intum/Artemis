import { Injectable, OnDestroy, inject } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';

type SubscribedChannel = { wsSubscription: Subscription; subject: Subject<any> };

/**
 * The IrisWebsocketService handles the websocket communication for receiving messages in dedicated channels.
 */
@Injectable({ providedIn: 'root' })
export class IrisWebsocketService implements OnDestroy {
    protected jhiWebsocketService = inject(JhiWebsocketService);

    private subscribedChannels: Map<number, SubscribedChannel> = new Map();

    /**
     * Cleans up resources before the service is destroyed.
     */
    ngOnDestroy(): void {
        this.subscribedChannels.forEach((subscription, sessionId) => {
            subscription.wsSubscription.unsubscribe();
            this.jhiWebsocketService.unsubscribe(this.getChannelFromSessionId(sessionId));
        });
    }

    /**
     * Subscribes to a session.
     * @param sessionId The session ID to subscribe to.
     */
    public subscribeToSession(sessionId: number): Observable<any> {
        if (!sessionId) {
            throw new Error('Session ID is required');
        }

        const subscribedChannel = this.subscribedChannels.computeIfAbsent(sessionId, () => {
            const channel = this.getChannelFromSessionId(sessionId);
            const subject = new Subject<any>();
            const wsSubscription = this.jhiWebsocketService
                .subscribe(channel)
                .receive(channel)
                .subscribe((response: any) => {
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

            const channel = this.getChannelFromSessionId(sessionId);
            this.jhiWebsocketService.unsubscribe(channel);
            return true;
        }
        return false;
    }

    private getChannelFromSessionId(sessionId: number) {
        return '/user/topic/iris/' + sessionId;
    }
}
