import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CourseNotificationSettingSpecification } from 'app/notification/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannelSetting } from 'app/notification/shared/entities/course-notification/course-notification-channel-setting';
import { CourseNotificationSettingInfo } from 'app/notification/shared/entities/course-notification/course-notification-setting-info';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { CourseNotificationSettingPreset } from 'app/notification/shared/entities/course-notification/course-notification-setting-preset';
import { AccountService } from 'app/core/auth/account.service';

/**
 * Service for managing course notification settings.
 * Provides methods to fetch and update notification preferences for courses.
 */
@Injectable({
    providedIn: 'root',
})
export class CourseNotificationSettingService implements OnDestroy {
    private readonly apiEndpoint = '/api/communication/notification/';

    private http = inject(HttpClient);
    private readonly accountService = inject(AccountService);

    private settingInfoSubjects: Record<number, BehaviorSubject<CourseNotificationSettingInfo | undefined>> = {};

    private stateGeneration = 0;
    private currentUserId?: number;
    private authenticationStateSubscription: Subscription;

    constructor() {
        this.currentUserId = this.accountService.userIdentity()?.id;
        this.authenticationStateSubscription = this.accountService.getAuthenticationState().subscribe((user) => {
            if (this.currentUserId !== user?.id) {
                this.currentUserId = user?.id;
                this.resetState();
            }
        });
    }

    ngOnDestroy(): void {
        this.authenticationStateSubscription?.unsubscribe();
    }

    /**
     * Clears the cached per-course setting subjects. Called on logout / user change so the next user
     * does not see the previous user's notification settings.
     */
    private resetState(): void {
        this.stateGeneration++;
        Object.values(this.settingInfoSubjects).forEach((subject) => subject.complete());
        this.settingInfoSubjects = {};
    }

    /**
     * Gets or creates a BehaviorSubject for a specific course's setting info
     *
     * @param courseId - The ID of the course
     * @returns BehaviorSubject for the course's setting info
     */
    private getSettingInfoSubject(courseId: number): BehaviorSubject<CourseNotificationSettingInfo | undefined> {
        if (!this.settingInfoSubjects[courseId]) {
            this.settingInfoSubjects[courseId] = new BehaviorSubject<CourseNotificationSettingInfo | undefined>(undefined);
        }
        return this.settingInfoSubjects[courseId];
    }

    /**
     * Retrieves notification settings information for a specific course.
     * Returns an Observable that will emit updates when settings change.
     *
     * @param courseId - The ID of the course
     * @param forceFetch - Skips the cache and forces an http request
     * @returns An Observable with the course notification setting information
     */
    getSettingInfo(courseId: number, forceFetch = false): Observable<CourseNotificationSettingInfo | undefined> {
        const subject = this.getSettingInfoSubject(courseId);

        if (!forceFetch && subject.getValue()) {
            return subject.asObservable().pipe(map((value) => value as CourseNotificationSettingInfo));
        }

        const generation = this.stateGeneration;
        this.http
            .get<CourseNotificationSettingInfo>(this.apiEndpoint + courseId + '/settings', { observe: 'response' })
            .pipe(
                tap((response) => {
                    if (this.stateGeneration !== generation) return;
                    if (response.body) {
                        subject.next(response.body);
                    }
                }),
            )
            .subscribe();

        return subject.asObservable().pipe(map((value) => value as CourseNotificationSettingInfo | undefined));
    }

    /**
     * Sets a notification setting preset for a course.
     * Updates the cached setting info.
     *
     * @param courseId - The ID of the course
     * @param presetTypeId - The ID of the preset to apply
     * @param copyPreset - The preset to copy the settings from
     */
    setSettingPreset(courseId: number, presetTypeId: number, copyPreset: CourseNotificationSettingPreset | undefined): void {
        const subject = this.getSettingInfoSubject(courseId);
        const currentValue = subject.getValue();

        if (currentValue) {
            if (copyPreset) {
                currentValue.notificationTypeChannels = copyPreset.presetMap;
            }
            const updatedValue = { ...currentValue, selectedPreset: presetTypeId };
            subject.next(updatedValue);
        }

        this.http.put(this.apiEndpoint + courseId + '/setting-preset', presetTypeId).subscribe();
    }

    /**
     * Updates individual notification settings for a course.
     * Forces a refresh of the setting info.
     *
     * @param courseId - The ID of the course
     * @param notificationSettingSpecification - Array of notification setting specifications to update
     * @param copyPreset - The preset to copy the settings from
     */
    setSettingSpecification(
        courseId: number,
        notificationSettingSpecification: CourseNotificationSettingSpecification,
        copyPreset: CourseNotificationSettingPreset | undefined,
    ): void {
        const subject = this.getSettingInfoSubject(courseId);
        const currentValue = subject.getValue();

        if (currentValue) {
            if (copyPreset) {
                currentValue.notificationTypeChannels = copyPreset.presetMap;
            }
            if (currentValue.notificationTypeChannels[notificationSettingSpecification.typeId]) {
                currentValue.notificationTypeChannels[notificationSettingSpecification.typeId] = notificationSettingSpecification.channelSetting;
            } else {
                currentValue.notificationTypeChannels[notificationSettingSpecification.identifier] = notificationSettingSpecification.channelSetting;
            }
            const updatedValue = { ...currentValue, selectedPreset: 0 };
            subject.next(updatedValue);
        }

        const notificationSettingSpecifications = [notificationSettingSpecification];

        this.http
            .put(this.apiEndpoint + courseId + '/setting-specification', {
                notificationTypeChannels: this.transformNotificationSettingSpecificationToRequestBody(notificationSettingSpecifications),
            })
            .subscribe();
    }

    /**
     * Transforms an array of notification setting specifications into the format required by the API.
     *
     * @param settings - Array of notification setting specifications
     * @returns Record mapping notification type IDs to channel settings
     */
    private transformNotificationSettingSpecificationToRequestBody(settings: CourseNotificationSettingSpecification[]): Record<number, CourseNotificationChannelSetting> {
        const result: Record<number, CourseNotificationChannelSetting> = {};

        settings.forEach((setting) => {
            result[setting.typeId] = setting.channelSetting;
        });

        return result;
    }
}
