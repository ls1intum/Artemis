import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { CourseNotificationSettingSpecification } from 'app/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannelSetting } from 'app/entities/course-notification/course-notification-channel-setting';
import { CourseNotificationSettingInfo } from 'app/entities/course-notification/course-notification-setting-info';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class CourseNotificationSettingService {
    private readonly apiEndpoint = '/api/communication/notification/';

    private http = inject(HttpClient);

    getSettingInfo(courseId: number): Observable<HttpResponse<CourseNotificationSettingInfo>> {
        return this.http.get<CourseNotificationSettingInfo>(this.apiEndpoint + courseId + '/settings', { observe: 'response' });
    }

    setSettingPreset(courseId: number, presetTypeId: number): void {
        this.http.put(this.apiEndpoint + courseId + '/setting-preset', presetTypeId).subscribe();
    }

    setSettingSpecification(courseId: number, notificationSettingSpecifications: CourseNotificationSettingSpecification[]): void {
        this.http
            .put(this.apiEndpoint + courseId + '/setting-specification', {
                notificationTypeChannels: this.transformNotificationSettingSpecificationToRequestBody(notificationSettingSpecifications),
            })
            .subscribe();
    }

    private transformNotificationSettingSpecificationToRequestBody(settings: CourseNotificationSettingSpecification[]): Record<number, CourseNotificationChannelSetting> {
        const result: Record<number, CourseNotificationChannelSetting> = {};

        settings.forEach((setting) => {
            result[setting.typeId] = setting.channelSetting;
        });

        return result;
    }
}
