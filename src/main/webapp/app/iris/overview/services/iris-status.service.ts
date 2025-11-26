import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, Subscription, firstValueFrom } from 'rxjs';
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
 * It also manages the current rate limits, which are course-specific.
 */
@Injectable({ providedIn: 'root' })
export class IrisStatusService implements OnDestroy {
    private readonly HEARTBEAT_INTERVAL_MS = 60 * 1000 * 5; // 5 minutes
    private websocketService = inject(WebsocketService);
    private httpClient = inject(HttpClient);
    private profileService = inject(ProfileService);

    intervalId: ReturnType<typeof setInterval> | undefined;
    websocketStatusSubscription: Subscription;
    disconnected = false;

    active = false;
    activeSubject = new BehaviorSubject<boolean>(this.active);

    currentRatelimitInfoSubject = new BehaviorSubject<IrisRateLimitInformation>(new IrisRateLimitInformation(0, 0, 0));

    private currentCourseId: number | undefined;

    /**
     * Creates an instance of IrisStatusService.
     */
    constructor() {
        if (!this.profileService.isProfileActive(PROFILE_IRIS)) {
            return;
        }

        this.websocketStatusSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.disconnected = !status.connected && !status.intendedDisconnect && status.wasEverConnectedBefore;
        });

        // Start heartbeat interval (will only fetch if courseId is set)
        this.intervalId = setInterval(() => {
            this.checkHeartbeat();
        }, this.HEARTBEAT_INTERVAL_MS);
    }

    /**
     * Sets the current course context and fetches updated status.
     * Should be called when the user navigates to a different course.
     * @param courseId The course ID to use for status checks
     */
    setCurrentCourse(courseId: number): void {
        if (this.currentCourseId !== courseId) {
            this.currentCourseId = courseId;
            this.checkHeartbeat();
        }
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
     * Requires a course ID to be set via setCurrentCourse().
     */
    private checkHeartbeat(): void {
        if (this.disconnected || !this.currentCourseId) return;
        firstValueFrom(this.getIrisStatus(this.currentCourseId)).then((response: HttpResponse<IrisStatusDTO>) => {
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
        this.websocketStatusSubscription?.unsubscribe();
    }

    /**
     * Checks whether Iris is active for the given course.
     * @param courseId The course ID to check status for
     * @return An Observable of the HTTP response containing the status.
     */
    private getIrisStatus(courseId: number): Response<IrisStatusDTO> {
        return this.httpClient.get<IrisStatusDTO>(`api/iris/courses/${courseId}/status`, { observe: 'response' });
    }
}
