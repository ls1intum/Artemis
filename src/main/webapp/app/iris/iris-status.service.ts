import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, Subscription, firstValueFrom } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Response } from 'app/iris/iris-chat-http.service';
import { IrisStatusDTO } from 'app/entities/iris/iris-health.model';
import { IrisRateLimitInformation } from 'app/entities/iris/iris-ratelimit-info.model';

/**
 * The `IrisHeartbeatService` is responsible for monitoring the health status of Iris.
 * It periodically sends HTTP requests to check if the system is available.
 * The availability status is distributed to other services.
 * It also manages the current ratelimts.
 */
@Injectable({ providedIn: 'root' })
export class IrisStatusService implements OnDestroy {
    private websocketService = inject(JhiWebsocketService);
    private httpClient = inject(HttpClient);

    intervalId: ReturnType<typeof setInterval> | undefined;
    websocketStatusSubscription: Subscription;
    disconnected = false;

    active = true;
    activeSubject = new BehaviorSubject<boolean>(this.active);

    currentRatelimitInfoSubject = new BehaviorSubject<IrisRateLimitInformation>(new IrisRateLimitInformation(0, 0, 0));

    /**
     * Creates an instance of IrisHeartbeatService.
     * @param websocketService The JhiWebsocketService for managing the websocket connection.
     * @param httpSessionService The IrisHttpChatSessionService for HTTP operations related to sessions.
     */
    constructor() {
        this.checkHeartbeat();
        this.intervalId = setInterval(() => {
            this.checkHeartbeat();
        }, 60000);

        this.websocketStatusSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.disconnected = !status.connected && !status.intendedDisconnect && status.wasEverConnectedBefore;
        });
    }

    /**
     * Returns an Observable of the current Iris availability status.
     * @return An Observable of the current Iris availability status.
     */
    getActiveStatus(): Observable<boolean> {
        return this.activeSubject.asObservable();
    }

    /**
     * Returns an Observable of the current Iris ratelimit information.
     * @return An Observable of the current Iris ratelimit information.
     */
    currentRatelimitInfo(): Observable<IrisRateLimitInformation> {
        return this.currentRatelimitInfoSubject.asObservable();
    }

    /**
     * Handles the ratelimit information received through other means, such as the websocket.
     * @param rateLimitInfo The ratelimit information
     */
    handleRateLimitInfo(rateLimitInfo: IrisRateLimitInformation): void {
        this.currentRatelimitInfoSubject.next(rateLimitInfo);
    }

    /**
     * Checks the availability of Iris by sending a heartbeat request.
     */
    private checkHeartbeat(): void {
        if (this.disconnected) return;
        firstValueFrom(this.getIrisStatus()).then((response: HttpResponse<IrisStatusDTO>) => {
            if (response.body) {
                this.active = Boolean(response.body.active);

                if (response.body.rateLimitInfo) {
                    this.currentRatelimitInfoSubject.next(response.body.rateLimitInfo);
                }
            } else {
                this.active = false;
            }
            this.activeSubject.next(this.active);
        });
    }

    /**
     * Performs cleanup when the service is destroyed.
     * Clears the interval and unsubscribes from observables.
     */
    ngOnDestroy(): void {
        if (this.intervalId !== undefined) clearInterval(this.intervalId);
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
