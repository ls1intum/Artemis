import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, OperatorFunction } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { distinctUntilChanged, filter, map, pairwise, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { ActivationStart, NavigationEnd, Router } from '@angular/router';
import { AlertService, AlertType } from 'app/core/util/alert.service';

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

@Injectable({ providedIn: 'root' })
export class ProfileToggleService {
    private infoUrl = 'management/info';

    private readonly topic = `/topic/management/profile-toggles`;
    private subject: BehaviorSubject<ActiveProfileToggles | undefined> = new BehaviorSubject<ActiveProfileToggles | undefined>(undefined);
    private subscriptionInitialized = false;

    private profileForCurrentRoute?: ProfileToggle;
    private errorShownForCurrentRoute = false;

    constructor(
        private websocketService: JhiWebsocketService,
        private alertService: AlertService,
        private router: Router,
        private http: HttpClient,
    ) {
        this.websocketService.onWebSocketConnected().subscribe(() => {
            this.reloadActiveProfileTogglesFromServer();
        });
        this.checkActiveRouteForActivatedProfiles();
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
        return this.subject
            .asObservable()
            .pipe(filter((activeProfiles) => !!activeProfiles) as OperatorFunction<ActiveProfileToggles | undefined, ActiveProfileToggles>, distinctUntilChanged());
    }

    /**
     * Getter method for the active profiles toggles as an observable.
     * Will check that the passed profile is enabled
     */
    getProfileToggleActive(profile: ProfileToggle): Observable<boolean> {
        return this.getProfileTogglesActive([profile]);
    }

    /**
     * Getter method for the active profiles toggles with synchronous response.
     * Will check that the passed profile is enabled
     */
    getProfileToggleActiveInstant(profile: ProfileToggle): boolean {
        return this.getProfileTogglesActiveInstant([profile]);
    }

    /**
     * Getter method for the active profiles toggles as an observable.
     * Will check that all passed profiles are enabled
     */
    getProfileTogglesActive(profiles: ProfileToggle[]): Observable<boolean> {
        return this.subject.asObservable().pipe(
            filter((activeProfiles) => !!activeProfiles),
            map((activeProfiles: ActiveProfileToggles) => {
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
     * Getter method for the active profiles toggles with synchronous response.
     * Will check that all passed profiles are enabled
     */
    getProfileTogglesActiveInstant(profiles: ProfileToggle[]): boolean {
        const activeProfiles = this.subject.value;
        if (activeProfiles && activeProfiles.includes(ProfileToggle.DECOUPLING)) {
            // If the 'Decoupling' proxy-profile is set -> Decoupling is activated -> Apply logic
            return profiles.every((profile) => activeProfiles.includes(profile));
        }
        // If the 'Decoupling' profile is not present -> No separation of logic -> Always allowed
        return true;
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

    /**
     * Register subscriptions that detect the currently active route and check if they require specific profiles which might be unavailable later.
     * Inform the user if the functionality becomes unavailable and also inform them if it is available again.
     */
    checkActiveRouteForActivatedProfiles(): void {
        // Store the required profile for the current route
        this.router.events
            ?.pipe(
                // We are only interested in the primary outlet since it contains the 'profile'-data
                filter((event) => event instanceof ActivationStart && event.snapshot.outlet === 'primary'),
            )
            .subscribe((event: ActivationStart) => {
                this.profileForCurrentRoute = event.snapshot.data.profile;
            });

        // When the user navigates to a new page -> Reset the error-shown state
        this.router.events?.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => (this.errorShownForCurrentRoute = false));

        // Calculate whether the currently required profile is (un-)available and inform the user
        this.getProfileToggles()
            .pipe(
                // Pass both the previous and new value
                pairwise(),
            )
            .subscribe(([previousActiveProfiles, newActiveProfiles]) => {
                if (!this.profileForCurrentRoute) {
                    return;
                }

                const previouslyAvailable = previousActiveProfiles.includes(this.profileForCurrentRoute);
                const nowAvailable = newActiveProfiles.includes(this.profileForCurrentRoute);

                if (previouslyAvailable && !nowAvailable && !this.errorShownForCurrentRoute) {
                    // No longer available
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        translationKey: 'artemisApp.profileToggle.alerts.pageFunctionalityNotAvailable',
                        message: 'Certain functionality in this page is currently not available. Contact an administrator if this issue persists.',
                    });
                    this.errorShownForCurrentRoute = true;
                } else if (!previouslyAvailable && nowAvailable && this.errorShownForCurrentRoute) {
                    // Available again
                    this.alertService.addAlert({
                        type: AlertType.SUCCESS,
                        translationKey: 'artemisApp.profileToggle.alerts.pageFunctionalityAvailable',
                        message: 'Temporary unavailable functionality is available again.',
                    });
                    this.errorShownForCurrentRoute = false;
                }
            });
    }
}
