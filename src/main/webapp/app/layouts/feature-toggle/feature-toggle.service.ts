import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { distinctUntilChanged, map, tap } from 'rxjs/operators';

export enum FeatureToggle {
    PROGRAMMING_EXERCISES = 'PROGRAMMING_EXERCISES',
}
export type ActiveFeatures = Array<FeatureToggle>;

const defaultActiveFeatureState: ActiveFeatures = Object.values(FeatureToggle);

@Injectable({ providedIn: 'root' })
export class FeatureToggleService {
    private readonly topic = `/topic/management/features`;
    private subject: BehaviorSubject<ActiveFeatures>;
    private subscriptionInitialized = false;

    constructor(private websocketService: JhiWebsocketService) {
        this.subject = new BehaviorSubject<ActiveFeatures>(defaultActiveFeatureState);
    }

    /**
     * This method is only supposed to be called by the account service once the user is logged in!
     */
    public subscribeFeatureToggleUpdates() {
        if (!this.subscriptionInitialized) {
            this.websocketService.subscribe(this.topic);
            this.websocketService
                .receive(this.topic)
                .pipe(tap(activeFeatures => this.notifySubscribers(activeFeatures)))
                .subscribe();
            this.subscriptionInitialized = true;
        }
    }

    /**
     * This method is only supposed to be called by the account service once the user is logged out!
     */
    public unsubscribeFeatureToggleUpdates() {
        if (this.subscriptionInitialized) {
            this.websocketService.unsubscribe(this.topic);
            this.subscriptionInitialized = false;
        }
    }

    private notifySubscribers(activeFeatures: ActiveFeatures) {
        this.subject.next(activeFeatures);
    }

    setFeatureToggles(activeFeatures: ActiveFeatures) {
        this.notifySubscribers(activeFeatures);
    }

    getFeatureToggles() {
        return this.subject.asObservable().pipe(distinctUntilChanged());
    }

    getFeatureToggleActive(feature: FeatureToggle) {
        return this.subject.asObservable().pipe(
            map(activeFeatures => activeFeatures.includes(feature)),
            distinctUntilChanged(),
        );
    }
}
