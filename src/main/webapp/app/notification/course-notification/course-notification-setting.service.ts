import { onUserChange } from 'app/core/auth/user-change.util';
import { OnDestroy, Service, inject } from '@angular/core';
import { CourseNotificationSettingSpecification } from 'app/notification/shared/entities/course-notification/course-notification-setting-specification';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { UserCourseNotificationSettingApi } from 'app/openapi/api/user-course-notification-setting-api';
import { CourseNotificationSettingInfo as SettingInfoDto } from 'app/openapi/model/course-notification-setting-info';
import { CourseNotificationSettingSpecificationRequest } from 'app/openapi/model/course-notification-setting-specification-request';
import { CourseNotificationSettingPreset } from 'app/notification/shared/entities/course-notification/course-notification-setting-preset';
import { AccountService } from 'app/core/auth/account.service';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

/** UI view: NON_EMPTY may omit an empty wire map; consumers always receive a map without invented channel defaults. */
export type CourseNotificationSettingInfo = SettingInfoDto & {
    notificationTypeChannels: NonNullable<SettingInfoDto['notificationTypeChannels']>;
};

/**
 * Service for managing course notification settings.
 * Provides methods to fetch and update notification preferences for courses.
 */
@Service()
export class CourseNotificationSettingService implements OnDestroy {
    private api = inject(UserCourseNotificationSettingApi);
    private readonly accountService = inject(AccountService);

    private settingInfoSubjects: Record<number, BehaviorSubject<CourseNotificationSettingInfo | undefined>> = {};

    private stateGeneration = 0;
    private authenticationStateSubscription: Subscription;

    constructor() {
        this.authenticationStateSubscription = onUserChange(this.accountService, () => this.resetState());
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
            return subject.asObservable();
        }

        const generation = this.stateGeneration;
        this.api.getUserCourseNotificationSettings(courseId).subscribe((response) => {
            if (this.stateGeneration !== generation) return;
            if (response) {
                subject.next({ selectedPreset: response.selectedPreset, notificationTypeChannels: response.notificationTypeChannels ?? {} });
            }
        });

        return subject.asObservable();
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
                // A copy: the preset is shared by every course, and the channels of this one are changed on their own afterwards.
                currentValue.notificationTypeChannels = deepClone(copyPreset.presetMap);
            }
            const updatedValue = cloneWith(currentValue, { selectedPreset: presetTypeId });
            subject.next(updatedValue);
        }

        this.api.setUserCourseNotificationPreset(courseId, presetTypeId).subscribe();
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
                // Customizing starts from the chosen preset. Without one, the channels in the cache are the ones that apply to the user
                // and stay as they are, so that changing one channel does not bring back what the effective settings leave out.
                currentValue.notificationTypeChannels = deepClone(copyPreset.presetMap);
            }
            if (currentValue.notificationTypeChannels[notificationSettingSpecification.typeId]) {
                currentValue.notificationTypeChannels[notificationSettingSpecification.typeId] = notificationSettingSpecification.channelSetting;
            } else {
                currentValue.notificationTypeChannels[notificationSettingSpecification.identifier] = notificationSettingSpecification.channelSetting;
            }
            const updatedValue = cloneWith(currentValue, { selectedPreset: 0 });
            subject.next(updatedValue);
        }

        const input: CourseNotificationSettingSpecificationRequest = {
            notificationTypeChannels: { [notificationSettingSpecification.typeId]: notificationSettingSpecification.channelSetting },
        };
        this.api.setUserCourseNotificationSpecification(courseId, input).subscribe();
    }
}
