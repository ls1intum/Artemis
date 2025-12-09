import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CourseNotificationSettingSpecification } from 'app/communication/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannelSetting } from 'app/communication/shared/entities/course-notification/course-notification-channel-setting';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { BehaviorSubject, Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { CourseNotificationSettingPreset } from 'app/communication/shared/entities/course-notification/course-notification-setting-preset';

/**
 * Service for managing course notification settings.
 * Provides methods to fetch and update notification preferences for courses.
 */
@Injectable({
    providedIn: 'root',
})
export class CourseNotificationSettingService {
    private readonly apiEndpoint = '/api/communication/notification/';

    private http = inject(HttpClient);

    private settingInfoSubjects: Record<number, BehaviorSubject<CourseNotificationSettingInfo | undefined>> = {};

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

        this.http
            .get<CourseNotificationSettingInfo>(this.apiEndpoint + courseId + '/settings', { observe: 'response' })
            .pipe(
                tap((response) => {
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
            const updatedValue = Object.assign({}, currentValue, { selectedPreset: presetTypeId });
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
            const updatedValue = Object.assign({}, currentValue, { selectedPreset: 0 });
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
