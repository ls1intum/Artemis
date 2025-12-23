import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { distinctUntilChanged, map, tap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

/**
 * FeatureToggles
 * Must be the same as in de.tum.cit.aet.artemis.core.service.feature.Feature on the server side
 * @readonly
 * @enum {string}
 */
export enum FeatureToggle {
    ProgrammingExercises = 'ProgrammingExercises',
    PlagiarismChecks = 'PlagiarismChecks',
    Exports = 'Exports',
    LearningPaths = 'LearningPaths',
    Science = 'Science',
    StandardizedCompetencies = 'StandardizedCompetencies',
    StudentCourseAnalyticsDashboard = 'StudentCourseAnalyticsDashboard',
    TutorSuggestions = 'TutorSuggestions',
    AtlasML = 'AtlasML',
    AtlasAgent = 'AtlasAgent',
    Memiris = 'Memiris',
    LectureContentProcessing = 'LectureContentProcessing',
}
export type ActiveFeatureToggles = Array<FeatureToggle>;

const defaultActiveFeatureState: ActiveFeatureToggles = Object.values(FeatureToggle);

@Injectable({ providedIn: 'root' })
export class FeatureToggleService {
    private websocketService = inject(WebsocketService);
    private http = inject(HttpClient);

    private readonly TOPIC = `/topic/management/feature-toggles`;
    private subject: BehaviorSubject<ActiveFeatureToggles>;
    private subscriptionInitialized = false;
    private featureToggleSubscription?: Subscription;

    constructor() {
        this.subject = new BehaviorSubject<ActiveFeatureToggles>(defaultActiveFeatureState);
    }

    /**
     * This method is only supposed to be called by the account service once the user is logged in!
     */
    public subscribeFeatureToggleUpdates() {
        if (!this.subscriptionInitialized) {
            this.featureToggleSubscription = this.websocketService
                .subscribe<ActiveFeatureToggles>(this.TOPIC)
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
            this.featureToggleSubscription?.unsubscribe();
            this.featureToggleSubscription = undefined;
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
     * Getter method for the feature toggles as an observable.
     */
    getFeatureToggles() {
        return this.subject.asObservable().pipe(distinctUntilChanged());
    }

    /**
     * Getter method for the active features toggles as an observable.
     * Will check that the passed feature is enabled
     */
    getFeatureToggleActive(feature: FeatureToggle) {
        return this.getFeatureTogglesActive([feature]);
    }

    /**
     * Getter method for the active features toggles as an observable.
     * Will check that all passed features are enabled
     */
    getFeatureTogglesActive(features: FeatureToggle[]) {
        return this.subject.asObservable().pipe(
            map((activeFeatures) => features.every((feature) => activeFeatures.includes(feature))),
            distinctUntilChanged(),
        );
    }

    /**
     * Setter method for the state of a feature toggle.
     */
    setFeatureToggleState(featureToggle: FeatureToggle, active: boolean) {
        const url = '/api/core/admin/feature-toggle';
        const toggleParam = { [featureToggle]: active };
        return this.http.put(url, toggleParam);
    }
}
