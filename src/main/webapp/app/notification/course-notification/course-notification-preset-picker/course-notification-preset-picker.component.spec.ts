import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseNotificationPresetPickerComponent } from 'app/notification/course-notification/course-notification-preset-picker/course-notification-preset-picker.component';
import { CourseNotificationSettingPreset } from 'app/notification/shared/entities/course-notification/course-notification-setting-preset';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { faBell, faBellSlash, faBullhorn, faSliders } from '@fortawesome/free-solid-svg-icons';
import { CourseNotificationChannel } from 'app/notification/shared/entities/course-notification/course-notification-channel';
import { CourseNotificationSettingsMap } from 'app/notification/shared/entities/course-notification/course-notification-settings-map';
import { TranslateService } from '@ngx-translate/core';

describe('CourseNotificationPresetPickerComponent', () => {
    let component: CourseNotificationPresetPickerComponent;
    let fixture: ComponentFixture<CourseNotificationPresetPickerComponent>;

    const mockPresets: CourseNotificationSettingPreset[] = [
        new CourseNotificationSettingPreset('preset1', 1, createMockSettingsMap(true, false, true)),
        new CourseNotificationSettingPreset('preset2', 2, createMockSettingsMap(false, true, true)),
    ];

    function createMockSettingsMap(push: boolean, email: boolean, webapp: boolean): CourseNotificationSettingsMap {
        return {
            notificationType: {
                [CourseNotificationChannel.PUSH]: push,
                [CourseNotificationChannel.EMAIL]: email,
                [CourseNotificationChannel.WEBAPP]: webapp,
            },
        };
    }

    // The menu entries are rendered in an overlay once the toggle was clicked, so they are found in the document body.
    const options = () => Array.from(document.body.querySelectorAll<HTMLElement>('[data-testid="course-notification-preset-picker-item"]'));
    const openMenu = () => {
        fixture.nativeElement.querySelector('[data-testid="course-notification-preset-picker-toggle"]').click();
        fixture.detectChanges();
    };

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseNotificationPresetPickerComponent, MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useValue: { instant: vi.fn((key: string) => key), get: vi.fn() } }],
        });

        TestBed.overrideComponent(CourseNotificationPresetPickerComponent, {
            remove: { imports: [TranslateDirective] },
            add: { imports: [MockDirective(TranslateDirective)] },
        });
        fixture = TestBed.createComponent(CourseNotificationPresetPickerComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('availableCourseSettingPresets', mockPresets);
        fixture.componentRef.setInput('selectedCourseSettingPreset', mockPresets[0]);

        fixture.detectChanges();
    });

    it('renders no options until the toggle is clicked', () => {
        expect(options()).toHaveLength(0);
    });

    it('announces the selected preset including the custom choice', () => {
        openMenu();
        const selected = vi.spyOn(component.onPresetSelected, 'emit');
        expect(options().map((option) => option.getAttribute('aria-current'))).toEqual(['true', null, null]);

        options()[1].click();
        expect(selected).toHaveBeenCalledWith(mockPresets[1].typeId);

        fixture.componentRef.setInput('selectedCourseSettingPreset', mockPresets[1]);
        openMenu();
        expect(options().map((option) => option.getAttribute('aria-current'))).toEqual([null, 'true', null]);

        options()[2].click();
        expect(selected).toHaveBeenLastCalledWith(0);

        fixture.componentRef.setInput('selectedCourseSettingPreset', undefined);
        openMenu();
        expect(options().map((option) => option.getAttribute('aria-current'))).toEqual([null, null, 'true']);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set selectedPresetLangKey based on selectedCourseSettingPreset', () => {
        expect(component['selectedPresetLangKey']()).toBe('artemisApp.courseNotification.preset.preset1.title');

        fixture.componentRef.setInput('selectedCourseSettingPreset', mockPresets[1]);
        fixture.detectChanges();

        expect(component['selectedPresetLangKey']()).toBe('artemisApp.courseNotification.preset.preset2.title');
    });

    it('should use customUserCourseNotificationSettingPreset key when selected preset is null', () => {
        fixture.componentRef.setInput('selectedCourseSettingPreset', null);

        fixture.detectChanges();

        expect(component['selectedPresetLangKey']()).toBe('artemisApp.courseNotification.preset.customUserCourseNotificationSettingPreset.title');
    });

    it('should emit onPresetSelected event with the correct preset ID when a preset is selected', () => {
        const emitSpy = vi.spyOn(component.onPresetSelected, 'emit');

        component['presetSelected'](2);

        expect(emitSpy).toHaveBeenCalledWith(2);
    });

    it('should emit 0 when the custom preset is selected', () => {
        const emitSpy = vi.spyOn(component.onPresetSelected, 'emit');

        component['presetSelected'](0);

        expect(emitSpy).toHaveBeenCalledWith(0);
    });

    it('should render a dedicated icon for each option and the toggle', () => {
        fixture.componentRef.setInput('selectedCourseSettingPreset', mockPresets[0]);
        fixture.detectChanges();
        openMenu();

        // The toggle shows the icon of the selected preset and a chevron, each of the two presets and the custom option has an icon.
        expect(fixture.nativeElement.querySelectorAll('fa-icon')).toHaveLength(2);
        options().forEach((option) => expect(option.querySelectorAll('fa-icon')).toHaveLength(1));

        expect(component.selectedCourseSettingPreset()).toBe(mockPresets[0]);
        expect(component.selectedCourseSettingPreset()?.identifier).toBe('preset1');
    });

    it('should map each preset identifier to its dedicated icon and fall back to the bell', () => {
        expect(component['getPresetIcon']('defaultUserCourseNotificationSettingPreset')).toBe(faBell);
        expect(component['getPresetIcon']('allActivityUserCourseNotificationSettingPreset')).toBe(faBullhorn);
        expect(component['getPresetIcon']('ignoreUserCourseNotificationSettingPreset')).toBe(faBellSlash);
        expect(component['getPresetIcon']('customUserCourseNotificationSettingPreset')).toBe(faSliders);
        // Custom preset (no identifier) and unknown identifiers fall back gracefully.
        expect(component['getPresetIcon'](undefined)).toBe(faSliders);
        expect(component['getPresetIcon']('somethingUnknown')).toBe(faBell);
    });

    it('should reflect the selected preset in the toggle icon', () => {
        fixture.componentRef.setInput(
            'selectedCourseSettingPreset',
            new CourseNotificationSettingPreset('ignoreUserCourseNotificationSettingPreset', 3, createMockSettingsMap(false, false, false)),
        );
        fixture.detectChanges();
        expect(component['selectedPresetIcon']()).toBe(faBellSlash);

        fixture.componentRef.setInput('selectedCourseSettingPreset', undefined);
        fixture.detectChanges();
        expect(component['selectedPresetIcon']()).toBe(faSliders);
    });

    it('should only bold the selected preset title in the dropdown', () => {
        fixture.componentRef.setInput('selectedCourseSettingPreset', mockPresets[0]);
        fixture.detectChanges();
        openMenu();

        const presetItems = options();
        expect(presetItems[0].querySelector('strong')).not.toBeNull();
        expect(presetItems[1].querySelector('strong')).toBeNull();
        expect(presetItems[2].querySelector('strong')).toBeNull();
    });
});
