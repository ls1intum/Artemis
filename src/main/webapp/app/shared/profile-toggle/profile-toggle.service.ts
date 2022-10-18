import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { distinctUntilChanged, map, tap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

/**
 * ProfileToggle, currently only supports LECTURE
 * @readonly
 * @enum {string}
 */
export enum ProfileToggle {
    LECTURE = 'lecture',
}
export type ActiveProfileToggles = Array<ProfileToggle>;

const defaultActiveProfileState: ActiveProfileToggles = Object.values(ProfileToggle);

@Injectable({ providedIn: 'root' })
export class ProfileToggleService {
    private readonly topic = `/topic/management/profile-toggles`;
    private subject: BehaviorSubject<ActiveProfileToggles>;
    private subscriptionInitialized = false;

    constructor(private websocketService: JhiWebsocketService, private http: HttpClient) {
        this.subject = new BehaviorSubject<ActiveProfileToggles>(defaultActiveProfileState);
    }

    /**
     * This method is only supposed to be called by the account service once the user is logged in!
     */
    public subscribeProfileToggleUpdates() {
        if (!this.subscriptionInitialized) {
            this.websocketService.subscribe(this.topic);
            this.websocketService
                .receive(this.topic)
                .pipe(tap((activeProfiles) => this.notifySubscribers(activeProfiles)))
                .subscribe();
            this.subscriptionInitialized = true;
        }
    }

    /**
     * This method is only supposed to be called by the account service once the user is logged out!
     */
    public unsubscribeProfileToggleUpdates() {
        if (this.subscriptionInitialized) {
            this.websocketService.unsubscribe(this.topic);
            this.subscriptionInitialized = false;
        }
    }

    private notifySubscribers(activeProfiles: ActiveProfileToggles) {
        this.subject.next(activeProfiles);
    }

    /**
     * Set the initial value of the Profile toggles. Use with care as the set value will be sent to all subscribers!
     * The profile toggle value updates are transmitted from the server to the client with a websocket,
     * so there should be no reason to set the values manually, other than on initialization.
     * @param activeProfiles
     */
    initializeProfileToggles(activeProfiles: ActiveProfileToggles) {
        this.notifySubscribers(activeProfiles);
    }

    /**
     * Getter method for the profile toggles as an observable.
     */
    getProfileToggles() {
        return this.subject.asObservable().pipe(distinctUntilChanged());
    }

    /**
     * Getter method for the active profiles toggles as an observable.
     * Will check that the passed profile is enabled
     */
    getProfileToggleActive(profile: ProfileToggle) {
        return this.getProfileTogglesActive([profile]);
    }

    /**
     * Getter method for the active profiles toggles as an observable.
     * Will check that all passed profiles are enabled
     */
    getProfileTogglesActive(profiles: ProfileToggle[]) {
        return this.subject.asObservable().pipe(
            map((activeProfiles) => profiles.every((profile) => activeProfiles.includes(profile))),
            distinctUntilChanged(),
        );
    }
}
