import { Injectable, OnDestroy } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';

type SubscribedChannel = { wsSubscription: Subscription; subject: Subject<any> };

/**
 * The IrisWebsocketService handles the websocket communication for receiving messages in dedicated channels.
 */
@Injectable({ providedIn: 'root' })
export class IrisWebsocketService implements OnDestroy {
    private subscribedChannels: Map<number, SubscribedChannel> = new Map();
    private subscribedTopics: Map<string, SubscribedChannel> = new Map();

    /**
     * Creates an instance of IrisWebsocketService.
     * @param jhiWebsocketService The JhiWebsocketService for websocket communication.
     */
    protected constructor(protected jhiWebsocketService: JhiWebsocketService) {}

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

    /**
     * Subscribes to a given topic and returns an observable for updates.
     * @param topic The topic to subscribe to.
     * @return Observable for receiving updates.
     */
    public subscribeToTopic(topic: string): Observable<any> {
        if (!topic) {
            throw new Error('Topic is required');
        }

        if (!this.subscribedTopics.has(topic)) {
            const subject = new Subject<any>();
            const wsSubscription = this.jhiWebsocketService
                .subscribe(topic)
                .receive(topic)
                .subscribe((response: any) => {
                    subject.next(response);
                });

            this.subscribedTopics.set(topic, { wsSubscription, subject });
        }

        return this.subscribedTopics.get(topic)!.subject.asObservable();
    }

    /**
     * Unsubscribes from a given topic.
     * @param topic The topic to unsubscribe from.
     */
    public unsubscribeFromTopic(topic: string): void {
        const subscribedTopic = this.subscribedTopics.get(topic);

        if (subscribedTopic) {
            subscribedTopic.wsSubscription.unsubscribe();
            this.subscribedTopics.delete(topic);
            this.jhiWebsocketService.unsubscribe(topic);
        }
    }
}
