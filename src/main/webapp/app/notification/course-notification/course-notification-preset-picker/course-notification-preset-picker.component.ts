import { Component, computed, input, output, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faBell, faBellSlash, faBullhorn, faSliders } from '@fortawesome/free-solid-svg-icons';
import { CourseNotificationSettingPreset } from 'app/notification/shared/entities/course-notification/course-notification-setting-preset';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { ButtonModule } from 'primeng/button';

/**
 * Component for selecting notification setting presets.
 * Displays a dropdown with available presets and handles selection events.
 */
@Component({
    selector: 'jhi-course-notification-preset-picker',
    imports: [TranslateDirective, FaIconComponent, NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgClass, ButtonModule],
    templateUrl: './course-notification-preset-picker.component.html',
    styleUrls: ['./course-notification-preset-picker.component.scss'],
})
export class CourseNotificationPresetPickerComponent {
    readonly availableCourseSettingPresets = input.required<CourseNotificationSettingPreset[]>();
    readonly selectedCourseSettingPreset = input.required<CourseNotificationSettingPreset | undefined>();
    readonly isSmallButton = input<boolean>(false);

    readonly onPresetSelected = output<number>();

    // Custom preset (no `identifier`) falls back to the sliders icon; unknown identifiers use the default bell.
    private static readonly presetIcons: Record<string, IconDefinition> = {
        defaultUserCourseNotificationSettingPreset: faBell,
        allActivityUserCourseNotificationSettingPreset: faBullhorn,
        ignoreUserCourseNotificationSettingPreset: faBellSlash,
        customUserCourseNotificationSettingPreset: faSliders,
    };

    private recentlySelectedTimeout?: NodeJS.Timeout;
    // `isRecentlySelected` is flipped back to false inside a setTimeout callback, and the lang key derives
    // from a signal input; both are read in the template, so they must be reactive under zoneless.
    protected readonly isRecentlySelected = signal(false);
    protected readonly selectedPresetLangKey = computed(() => {
        const identifier = this.selectedCourseSettingPreset()?.identifier ?? 'customUserCourseNotificationSettingPreset';
        return 'artemisApp.courseNotification.preset.' + identifier + '.title';
    });

    /**
     * The icon shown on the toggle button, reflecting the currently selected preset
     * (or the custom preset when none is selected).
     */
    protected readonly selectedPresetIcon = computed(() => this.getPresetIcon(this.selectedCourseSettingPreset()?.identifier));

    /**
     * Returns the dedicated icon for a preset identifier so each option is visually distinguishable.
     *
     * @param identifier - The preset identifier, or undefined for the custom preset
     * @returns The FontAwesome icon representing the preset
     */
    protected getPresetIcon(identifier: string | undefined): IconDefinition {
        return CourseNotificationPresetPickerComponent.presetIcons[identifier ?? 'customUserCourseNotificationSettingPreset'] ?? faBell;
    }

    /**
     * Handles preset selection from the dropdown.
     * Emits the selected preset's type ID to the parent component.
     *
     * @param presetTypeId - The type ID of the selected preset
     */
    protected presetSelected(presetTypeId: number) {
        this.isRecentlySelected.set(true);
        if (this.recentlySelectedTimeout) {
            clearTimeout(this.recentlySelectedTimeout);
        }

        this.recentlySelectedTimeout = setTimeout(() => {
            this.isRecentlySelected.set(false);
        }, 5000);

        this.onPresetSelected.emit(presetTypeId);
    }
}
