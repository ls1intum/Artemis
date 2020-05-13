import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { distinctUntilChanged, map, tap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

/**
 * FeatureToggle, currently only supports 'PROGRAMMING_EXERCISES'
 * @readonly
 * @enum {string}
 */
export enum FeatureToggle {
    PROGRAMMING_EXERCISES = 'PROGRAMMING_EXERCISES',
}
export type ActiveFeatureToggles = Array<FeatureToggle>;

const defaultActiveFeatureState: ActiveFeatureToggles = Object.values(FeatureToggle);

@Injectable({ providedIn: 'root' })
export class FeatureToggleService {
    private readonly topic = `/topic/management/feature-toggles`;
    private subject: BehaviorSubject<ActiveFeatureToggles>;
    private subscriptionInitialized = false;

    constructor(private websocketService: JhiWebsocketService, private http: HttpClient) {
        this.subject = new BehaviorSubject<ActiveFeatureToggles>(defaultActiveFeatureState);
    }

    /**
     * This method is only supposed to be called by the account service once the user is logged in!
     */
    public subscribeFeatureToggleUpdates() {
        if (!this.subscriptionInitialized) {
            this.websocketService.subscribe(this.topic);
            this.websocketService
                .receive(this.topic)
                .pipe(tap((activeFeatures) => this.notifySubscribers(activeFeatures)))
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

    private notifySubscribers(activeFeatures: ActiveFeatureToggles) {
        this.subject.next(activeFeatures);
    }

    /**
     * Set the initial value of the feature toggles. Use with care as the set value will be sent to all subscribers!
     * The feature toggle value updates are transmitted from the server to the client with a websocket,
     * so there should be no reason to set the values manually, other than on initialization.
     * @param activeFeatures
     */
    initializeFeatureToggles(activeFeatures: ActiveFeatureToggles) {
        this.notifySubscribers(activeFeatures);
    }

    /**
     * Getter method for FeatureToggles. Returns a new {@link Observable} with {@link subject} as the source.
     * @method
     */
    getFeatureToggles() {
        return this.subject.asObservable().pipe(distinctUntilChanged());
    }

    /**
     * Getter method for the active status of the FeatureToggle.
     * @method
     * @param {FeatureToggle} feature
     */
    getFeatureToggleActive(feature: FeatureToggle) {
        return this.subject.asObservable().pipe(
            map((activeFeatures) => activeFeatures.includes(feature)),
            distinctUntilChanged(),
        );
    }

    /** Sets the state of a {@link FeatureToggle} to {@param active}.
     * @method
     * @param {FeatureToggle} featureToggle
     * @param {boolean} active
     */
    setFeatureToggleState(featureToggle: FeatureToggle, active: boolean) {
        const url = '/api/management/feature-toggle';
        const toggleParam = { [featureToggle]: active };
        return this.http.put(url, toggleParam);
    }
}
