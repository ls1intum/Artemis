import { Component, effect, input, output } from '@angular/core';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faEye, faEyeSlash } from '@fortawesome/free-regular-svg-icons';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { CourseNotificationSettingPreset } from 'app/communication/shared/entities/course-notification/course-notification-setting-preset';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';

/**
 * Component for selecting notification setting presets.
 * Displays a dropdown with available presets and handles selection events.
 */
@Component({
    selector: 'jhi-course-notification-preset-picker',
    imports: [TranslateDirective, FaIconComponent, NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgClass],
    templateUrl: './course-notification-preset-picker.component.html',
    styleUrls: ['./course-notification-preset-picker.component.scss'],
})
export class CourseNotificationPresetPickerComponent {
    readonly availableCourseSettingPresets = input.required<CourseNotificationSettingPreset[]>();
    readonly selectedCourseSettingPreset = input.required<CourseNotificationSettingPreset | undefined>();
    readonly isSmallButton = input<boolean>(false);

    readonly onPresetSelected = output<number>();

    // Icons
    protected readonly farEye = faEye;
    protected readonly farEyeSlash = faEyeSlash;
    protected readonly faCheck = faCheck;

    private recentlySelectedTimeout: NodeJS.Timeout;
    protected isRecentlySelected: boolean = false;
    protected selectedPresetLangKey: string;

    constructor() {
        effect(() => {
            const identifier = this.selectedCourseSettingPreset()?.identifier ?? 'customUserCourseNotificationSettingPreset';

            this.selectedPresetLangKey = 'artemisApp.courseNotification.preset.' + identifier + '.title';
        });
    }

    /**
     * Handles preset selection from the dropdown.
     * Emits the selected preset's type ID to the parent component.
     *
     * @param presetTypeId - The type ID of the selected preset
     */
    protected presetSelected(presetTypeId: number) {
        this.isRecentlySelected = true;
        if (this.recentlySelectedTimeout) {
            clearTimeout(this.recentlySelectedTimeout);
        }

        this.recentlySelectedTimeout = setTimeout(() => {
            this.isRecentlySelected = false;
        }, 5000);

        this.onPresetSelected.emit(presetTypeId);
    }
}
