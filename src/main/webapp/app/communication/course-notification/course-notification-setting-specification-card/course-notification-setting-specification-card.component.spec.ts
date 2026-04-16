import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseNotificationSettingSpecificationCardComponent } from 'app/communication/course-notification/course-notification-setting-specification-card/course-notification-setting-specification-card.component';
import { CourseNotificationSettingSpecification } from 'app/communication/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannel } from 'app/communication/shared/entities/course-notification/course-notification-channel';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseNotificationChannelSetting } from 'app/communication/shared/entities/course-notification/course-notification-channel-setting';
import { TranslateService } from '@ngx-translate/core';

describe('CourseNotificationSettingSpecificationCardComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseNotificationSettingSpecificationCardComponent;
    let fixture: ComponentFixture<CourseNotificationSettingSpecificationCardComponent>;

    const testChannelSetting: CourseNotificationChannelSetting = {
        [CourseNotificationChannel.PUSH]: true,
        [CourseNotificationChannel.EMAIL]: false,
        [CourseNotificationChannel.WEBAPP]: true,
    };

    const testSpecification = new CourseNotificationSettingSpecification('newPostNotification', 1, testChannelSetting);

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseNotificationSettingSpecificationCardComponent, FormsModule, MockDirective(TranslateDirective), MockComponent(CourseNotificationComponent)],
            providers: [{ provide: TranslateService, useValue: { instant: vi.fn((key: string) => key), get: vi.fn() } }],
        });
        TestBed.overrideComponent(CourseNotificationSettingSpecificationCardComponent, {
            remove: { imports: [TranslateDirective, CourseNotificationComponent] },
            add: { imports: [MockDirective(TranslateDirective), MockComponent(CourseNotificationComponent)] },
        });
        fixture = TestBed.createComponent(CourseNotificationSettingSpecificationCardComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('settingSpecification', testSpecification);

        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize component properties from the input specification', () => {
        expect(component['titleLangKey']).toBe('artemisApp.courseNotification.newPostNotification.settingsTitle');
        expect(component['typeId']).toBe(1);
        expect(component['channels']).toEqual(testChannelSetting);

        expect(component['mockNotification']).toBeDefined();
        expect(component['mockNotification'].notificationType).toBe('newPostNotification');
        expect(component['mockNotification'].notificationId).toBe(1);
    });

    it('should render notification preview using CourseNotificationComponent', () => {
        const notificationComponent = fixture.debugElement.query(By.directive(CourseNotificationComponent));
        expect(notificationComponent).not.toBeNull();

        const courseNotification = notificationComponent.componentInstance.courseNotification;
        const value = typeof courseNotification === 'function' ? courseNotification() : courseNotification;
        expect(value).toBe(component['mockNotification']);
    });

    it('should disable channels based on CourseNotificationService.DISABLE_NOTIFICATION_CHANNEL_TYPES', () => {
        fixture.detectChanges();

        expect(component['isDisabled'](CourseNotificationChannel.EMAIL, 'newPostNotification')).toBe(true);
        expect(component['isDisabled'](CourseNotificationChannel.PUSH, 'newPostNotification')).toBe(false);
        expect(component['isDisabled'](CourseNotificationChannel.WEBAPP, 'newPostNotification')).toBe(false);

        const isDisabledSpy = vi.spyOn(component as any, 'isDisabled');

        fixture.detectChanges();

        expect(isDisabledSpy).toHaveBeenCalledWith(CourseNotificationChannel.EMAIL, 'newPostNotification');
        expect(isDisabledSpy).toHaveBeenCalledWith(CourseNotificationChannel.PUSH, 'newPostNotification');
        expect(isDisabledSpy).toHaveBeenCalledWith(CourseNotificationChannel.WEBAPP, 'newPostNotification');
    });

    it('should create a new specification when optionChanged is called', () => {
        component['channels'][CourseNotificationChannel.PUSH] = false;

        const emitSpy = vi.spyOn(component.onOptionChanged, 'emit');

        component['optionChanged']();

        expect(emitSpy).toHaveBeenCalledOnce();
        const emittedSpec = emitSpy.mock.calls[0][0];

        expect(emittedSpec).toBeInstanceOf(CourseNotificationSettingSpecification);
        expect(emittedSpec.typeId).toBe(component['typeId']);
        expect(emittedSpec.identifier).toBe(component['titleLangKey']);
        expect(emittedSpec.channelSetting).toEqual(component['channels']);
        expect(emittedSpec.channelSetting[CourseNotificationChannel.PUSH]).toBe(false);
    });
});
