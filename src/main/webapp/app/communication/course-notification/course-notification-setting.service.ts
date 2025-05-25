import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { CourseNotificationSettingSpecification } from 'app/communication/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannelSetting } from 'app/communication/shared/entities/course-notification/course-notification-channel-setting';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { Observable } from 'rxjs';

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

    /**
     * Retrieves notification settings information for a specific course.
     *
     * @param courseId - The ID of the course
     * @returns An Observable with the course notification setting information
     */
    getSettingInfo(courseId: number): Observable<HttpResponse<CourseNotificationSettingInfo>> {
        return this.http.get<CourseNotificationSettingInfo>(this.apiEndpoint + courseId + '/settings', { observe: 'response' });
    }

    /**
     * Sets a notification setting preset for a course.
     *
     * @param courseId - The ID of the course
     * @param presetTypeId - The ID of the preset to apply
     */
    setSettingPreset(courseId: number, presetTypeId: number): void {
        this.http.put(this.apiEndpoint + courseId + '/setting-preset', presetTypeId).subscribe();
    }

    /**
     * Updates individual notification settings for a course.
     *
     * @param courseId - The ID of the course
     * @param notificationSettingSpecifications - Array of notification setting specifications to update
     */
    setSettingSpecification(courseId: number, notificationSettingSpecifications: CourseNotificationSettingSpecification[]): void {
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
