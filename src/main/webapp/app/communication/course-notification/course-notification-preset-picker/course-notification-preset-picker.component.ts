import { Component, effect, input, output } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faEye } from '@fortawesome/free-regular-svg-icons';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { CourseNotificationSettingPreset } from 'app/entities/course-notification/course-notification-setting-preset';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';

/**
 * Component for selecting notification setting presets.
 * Displays a dropdown with available presets and handles selection events.
 */
@Component({
    selector: 'jhi-course-notification-preset-picker',
    imports: [TranslateDirective, FaIconComponent, NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle],
    templateUrl: './course-notification-preset-picker.component.html',
    styleUrls: ['./course-notification-preset-picker.component.scss'],
})
export class CourseNotificationPresetPickerComponent {
    readonly availableCourseSettingPresets = input.required<CourseNotificationSettingPreset[]>();
    readonly selectedCourseSettingPreset = input.required<CourseNotificationSettingPreset | undefined>();

    readonly onPresetSelected = output<number>();

    // Icons
    protected readonly farEye = faEye;
    protected readonly faCheck = faCheck;

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
        this.onPresetSelected.emit(presetTypeId);
    }
}
