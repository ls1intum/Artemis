import { Component, OnDestroy, inject, signal } from '@angular/core';
import { CourseSettingCategoryDirective } from 'app/course/overview/course-settings/directive/course-setting-category.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBell, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { CourseNotificationSettingPreset } from 'app/notification/shared/entities/course-notification/course-notification-setting-preset';
import { CourseNotificationPresetPickerComponent } from 'app/notification/course-notification/course-notification-preset-picker/course-notification-preset-picker.component';
import { CourseNotificationSettingSpecification } from 'app/notification/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationSettingSpecificationCardComponent } from 'app/notification/course-notification/course-notification-setting-specification-card/course-notification-setting-specification-card.component';
import { CourseNotificationSettingService } from 'app/notification/course-notification/course-notification-setting.service';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { CourseNotificationInfo } from 'app/notification/shared/entities/course-notification/course-notification-info';
import { CourseNotificationSettingInfo } from 'app/notification/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationSettingsMap } from 'app/notification/shared/entities/course-notification/course-notification-settings-map';

/**
 * Component that manages notification settings for a course.
 * Extends CourseSettingCategoryDirective to integrate with the course settings framework.
 */
@Component({
    selector: 'jhi-notification-settings',
    imports: [TranslateDirective, FaIconComponent, CourseNotificationPresetPickerComponent, CourseNotificationSettingSpecificationCardComponent],
    templateUrl: './notification-settings.component.html',
    styleUrl: './notification-settings.component.scss',
})
export class NotificationSettingsComponent extends CourseSettingCategoryDirective implements OnDestroy {
    protected readonly courseNotificationSettingService: CourseNotificationSettingService = inject(CourseNotificationSettingService);
    protected readonly courseNotificationService: CourseNotificationService = inject(CourseNotificationService);

    // Icons
    protected readonly faBell = faBell;
    protected readonly faSpinner = faSpinner;

    protected readonly selectableSettingPresets = signal<CourseNotificationSettingPreset[]>(undefined!);
    protected readonly selectedSettingPreset = signal<CourseNotificationSettingPreset | undefined>(undefined);
    protected readonly isLoading = signal(true);
    protected settingInfo?: CourseNotificationSettingInfo;
    protected info?: CourseNotificationInfo;
    protected readonly notificationSpecifications = signal<CourseNotificationSettingSpecification[]>([]);

    constructor() {
        super();
    }

    /**
     * Initializes component values once both settingInfo and info are available.
     * Sets up selectable presets, current preset, and notification specifications.
     */
    initializeValues() {
        this.selectableSettingPresets.set(this.info!.presets);

        this.selectedSettingPreset.set(
            this.settingInfo!.selectedPreset === 0 ? undefined : this.selectableSettingPresets().find((preset) => preset.typeId === this.settingInfo!.selectedPreset)!,
        );

        if (this.settingInfo!.selectedPreset === 0) {
            this.updateSpecificationArrayByNotificationMap(this.settingInfo!.notificationTypeChannels, this.settingInfo!.notificationTypeChannels[1] === undefined);
        } else if (this.settingInfo!.selectedPreset !== 0) {
            this.updateSpecificationArrayByNotificationMap(this.selectedSettingPreset()!.presetMap, true);
        }

        this.isLoading.set(false);
    }

    /**
     * Handles selection of a notification preset.
     * Updates UI and schedules settings update to server with debouncing.
     *
     * @param presetTypeId - The ID of the selected preset (0 for custom settings)
     */
    presetSelected(presetTypeId: number) {
        this.selectedSettingPreset.set(presetTypeId === 0 ? undefined : this.selectableSettingPresets().find((preset) => preset.typeId === presetTypeId)!);

        this.courseNotificationSettingService.setSettingPreset(this.courseId, presetTypeId, this.selectedSettingPreset());
    }

    /**
     * Handles changes to individual notification settings.
     * Resets selected preset to custom and schedules settings update with debouncing.
     *
     * @param specification - The notification specification that was changed
     */
    optionChanged(specification: CourseNotificationSettingSpecification) {
        this.courseNotificationSettingService.setSettingSpecification(this.courseId, specification, this.selectedSettingPreset());

        this.selectedSettingPreset.set(undefined);
    }

    /**
     * Lifecycle hook from CourseSettingCategoryDirective.
     * Called when the course information becomes available.
     * Empty implementation as initialization is handled in onCourseIdAvailable.
     */
    onCourseAvailable(): void {}

    /**
     * Lifecycle hook from CourseSettingCategoryDirective.
     * Called when the course ID becomes available.
     * Fetches notification settings and info from the server.
     */
    onCourseIdAvailable(): void {
        this.courseNotificationSettingService.getSettingInfo(this.courseId, true).subscribe((settingInfo) => {
            if (settingInfo) {
                this.settingInfo = settingInfo;

                if (this.info) {
                    this.initializeValues();
                }
            }
        });

        this.courseNotificationService.getInfo().subscribe((info) => {
            if (info.body) {
                this.info = info.body;

                if (this.settingInfo) {
                    this.initializeValues();
                }
            }
        });
    }

    ngOnDestroy() {
        super.ngOnDestroy();
    }

    /**
     * Creates notification specifications from a notification map.
     * Used to update the UI based on either user selections or preset values.
     *
     * @param notificationMap - Map of notification types to channel settings
     * @param useValue - Whether to use the value (true) or key (false) as the lookup in the map
     */
    private updateSpecificationArrayByNotificationMap(notificationMap: CourseNotificationSettingsMap, useValue: boolean) {
        const notificationSpecifications: CourseNotificationSettingSpecification[] = [];
        Object.entries(this.info!.notificationTypes).forEach(([key, value]) => {
            notificationSpecifications.push(new CourseNotificationSettingSpecification(value, Number(key), notificationMap[useValue ? value : key]));
        });
        this.notificationSpecifications.set(notificationSpecifications);
    }
}
