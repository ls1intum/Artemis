import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { distinctUntilChanged, map, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { HttpClient, HttpResponse } from '@angular/common/http';

/**
 * ProfileToggle, currently only supports LECTURE
 * @readonly
 * @enum {string}
 */
export enum ProfileToggle {
    LECTURE = 'lecture',
    DECOUPLING = 'decoupling',
}
export type ActiveProfileToggles = Array<ProfileToggle>;

const defaultActiveProfileState: ActiveProfileToggles = Object.values(ProfileToggle);

@Injectable({ providedIn: 'root' })
export class ProfileToggleService {
    private infoUrl = 'management/info';

    private readonly topic = `/topic/management/profile-toggles`;
    private subject: BehaviorSubject<ActiveProfileToggles>;
    private subscriptionInitialized = false;

    constructor(private websocketService: JhiWebsocketService, private http: HttpClient) {
        this.subject = new BehaviorSubject<ActiveProfileToggles>(defaultActiveProfileState);
        this.websocketService.onWebSocketConnected().subscribe(() => {
            this.reloadActiveProfileTogglesFromServer();
        });
    }

    /**
     * This method is only supposed to be called by the account service once the user is logged in!
     */
    public subscribeProfileToggleUpdates(): void {
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
    public unsubscribeProfileToggleUpdates(): void {
        if (this.subscriptionInitialized) {
            this.websocketService.unsubscribe(this.topic);
            this.subscriptionInitialized = false;
        }
    }

    private notifySubscribers(activeProfiles: ActiveProfileToggles): void {
        this.subject.next(activeProfiles);
    }

    /**
     * Set the initial value of the Profile toggles. Use with care as the set value will be sent to all subscribers!
     * The profile toggle value updates are transmitted from the server to the client with a websocket,
     * so there should be no reason to set the values manually, other than on initialization.
     * @param activeProfiles
     */
    initializeProfileToggles(activeProfiles: ActiveProfileToggles): void {
        this.notifySubscribers(activeProfiles);
    }

    /**
     * Getter method for the profile toggles as an observable.
     */
    getProfileToggles(): Observable<ActiveProfileToggles> {
        return this.subject.asObservable().pipe(distinctUntilChanged());
    }

    /**
     * Getter method for the active profiles toggles as an observable.
     * Will check that the passed profile is enabled
     */
    getProfileToggleActive(profile: ProfileToggle): Observable<boolean> {
        return this.getProfileTogglesActive([profile]);
    }

    /**
     * Getter method for the active profiles toggles as an observable.
     * Will check that all passed profiles are enabled
     */
    getProfileTogglesActive(profiles: ProfileToggle[]): Observable<boolean> {
        return this.subject.asObservable().pipe(
            map((activeProfiles) => {
                if (activeProfiles.includes(ProfileToggle.DECOUPLING)) {
                    // If the 'Decoupling' proxy-profile is set -> Decoupling is activated -> Apply logic
                    return profiles.every((profile) => activeProfiles.includes(profile));
                }
                // If the 'Decoupling' profile is not present -> No separation of logic -> Always allowed
                return true;
            }),
            distinctUntilChanged(),
        );
    }

    /**
     * Reload the active profile toggles from the server.
     * This is called after a new WebSocket connection has been established, since the data received from the initial management call
     * might be outdated (e.g. if the client was connected to an instance that shut down and thus could not send out toggle updates).
     */
    reloadActiveProfileTogglesFromServer(): void {
        this.http.get<ProfileInfo>(this.infoUrl, { observe: 'response' }).subscribe((res: HttpResponse<ProfileInfo>) => {
            const data = res.body!;
            this.notifySubscribers(data.combinedProfiles);
        });
    }
}
