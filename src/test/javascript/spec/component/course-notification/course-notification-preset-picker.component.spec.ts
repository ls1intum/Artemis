import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseNotificationPresetPickerComponent } from 'app/communication/course-notification/course-notification-preset-picker/course-notification-preset-picker.component';
import { CourseNotificationSettingPreset } from 'app/entities/course-notification/course-notification-setting-preset';
import { By } from '@angular/platform-browser';
import { MockDirective, MockComponent } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { CourseNotificationChannel } from 'app/entities/course-notification/course-notification-channel';
import { CourseNotificationSettingsMap } from 'app/entities/course-notification/course-notification-settings-map';

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                CourseNotificationPresetPickerComponent,
                MockDirective(TranslateDirective),
                MockComponent(FaIconComponent),
                MockDirective(NgbDropdown),
                MockDirective(NgbDropdownToggle),
                MockDirective(NgbDropdownMenu),
                MockDirective(NgbDropdownItem),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseNotificationPresetPickerComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('availableCourseSettingPresets', mockPresets);
        fixture.componentRef.setInput('selectedCourseSettingPreset', mockPresets[0]);

        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set selectedPresetLangKey based on selectedCourseSettingPreset', () => {
        expect(component['selectedPresetLangKey']).toBe('artemisApp.courseNotification.preset.preset1.title');

        fixture.componentRef.setInput('selectedCourseSettingPreset', mockPresets[1]);
        fixture.detectChanges();

        expect(component['selectedPresetLangKey']).toBe('artemisApp.courseNotification.preset.preset2.title');
    });

    it('should use customUserCourseNotificationSettingPreset key when selected preset is null', () => {
        fixture.componentRef.setInput('selectedCourseSettingPreset', null);

        fixture.detectChanges();

        expect(component['selectedPresetLangKey']).toBe('artemisApp.courseNotification.preset.customUserCourseNotificationSettingPreset.title');
    });

    it('should emit onPresetSelected event with the correct preset ID when a preset is selected', () => {
        const emitSpy = jest.spyOn(component.onPresetSelected, 'emit');

        component['presetSelected'](2);

        expect(emitSpy).toHaveBeenCalledWith(2);
    });

    it('should emit 0 when the custom preset is selected', () => {
        const emitSpy = jest.spyOn(component.onPresetSelected, 'emit');

        component['presetSelected'](0);

        expect(emitSpy).toHaveBeenCalledWith(0);
    });

    it('should show checkmark icon for the selected preset', () => {
        fixture.componentRef.setInput('selectedCourseSettingPreset', mockPresets[0]);
        fixture.detectChanges();

        const iconComponents = fixture.debugElement.queryAll(By.directive(FaIconComponent));

        expect(iconComponents.length).toBeGreaterThan(0);

        expect(component.selectedCourseSettingPreset()).toBe(mockPresets[0]);

        expect(component.selectedCourseSettingPreset()?.identifier).toBe('preset1');
        expect(component.selectedCourseSettingPreset() !== null).toBeTrue();
    });
});
