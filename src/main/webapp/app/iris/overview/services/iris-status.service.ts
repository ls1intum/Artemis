import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, EMPTY, Observable, Subject, Subscription, map, shareReplay, startWith, switchMap, takeUntil, timer } from 'rxjs';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { Response } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisStatusDTO } from 'app/iris/shared/entities/iris-health.model';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';

/**
 * The `IrisStatusService` is responsible for monitoring the health status of Iris.
 * It periodically sends HTTP requests to check if the system is available.
 * The availability status is distributed to other services.
 * It also manages the current rate limits.
 */
@Injectable({ providedIn: 'root' })
export class IrisStatusService implements OnDestroy {
    private websocketService = inject(WebsocketService);
    private httpClient = inject(HttpClient);
    private profileService = inject(ProfileService);

    private disconnected = false;
    private unSubscribe = new Subject<void>();
    private ratelimitOverride$ = new BehaviorSubject<IrisRateLimitInformation>(new IrisRateLimitInformation(0, 0, 0));
    private shouldPoll = this.profileService.isProfileActive(PROFILE_IRIS);
    private websocketStatusSubscription: Subscription;

    /**
     * Creates an instance of IrisHeartbeatService.
     */
    constructor() {
        if (!this.shouldPoll) {
            return;
        }
        this.websocketStatusSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.disconnected = !status.connected && !status.intendedDisconnect && status.wasEverConnectedBefore;
        });
    }

    private statusPoll$: Observable<HttpResponse<IrisStatusDTO>> = this.shouldPoll
        ? timer(0, 60000).pipe(switchMap(() => (this.disconnected ? EMPTY : this.getIrisStatus())))
        : EMPTY;

    private activeStatus$: Observable<boolean> = this.statusPoll$.pipe(
        map((response) => Boolean(response.body?.active)),
        startWith(false),
        shareReplay({ refCount: true, bufferSize: 1 }),
    );

    private ratelimitInfo$: Observable<IrisRateLimitInformation> = this.statusPoll$.pipe(
        map((response) => response.body?.rateLimitInfo ?? new IrisRateLimitInformation(0, 0, 0)),
        startWith(new IrisRateLimitInformation(0, 0, 0)),
        switchMap((polledInfo) =>
            this.ratelimitOverride$.pipe(
                map((override) => override ?? polledInfo),
                takeUntil(this.statusPoll$),
            ),
        ),
        shareReplay({ refCount: true, bufferSize: 1 }),
    );

    /**
     * Returns an Observable of the current Iris availability status.
     */
    getActiveStatus(): Observable<boolean> {
        return this.activeStatus$;
    }

    /**
     * Returns an Observable of the current Iris ratelimit information.
     */
    currentRatelimitInfo(): Observable<IrisRateLimitInformation> {
        return this.ratelimitInfo$;
    }

    /**
     * Handles the ratelimit information received through other means, such as the websocket.
     * @param rateLimitInfo The ratelimit information
     */
    handleRateLimitInfo(rateLimitInfo: IrisRateLimitInformation): void {
        this.ratelimitOverride$.next(rateLimitInfo);
    }

    /**
     * Performs cleanup when the service is destroyed.
     */
    ngOnDestroy(): void {
        this.unSubscribe.next();
        this.unSubscribe.complete();
        this.websocketStatusSubscription.unsubscribe();
    }

    /**
     * Checks whether Iris is active.
     * @return An Observable of the HTTP response containing a boolean value indicating the status.
     */
    private getIrisStatus(): Response<IrisStatusDTO> {
        return this.httpClient.get<IrisStatusDTO>(`api/iris/status`, { observe: 'response' });
    }
}
