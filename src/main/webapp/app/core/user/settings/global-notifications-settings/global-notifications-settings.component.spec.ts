import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { GLOBAL_NOTIFICATION_TYPES, GlobalNotificationsSettingsComponent } from './global-notifications-settings.component';
import { GlobalNotificationSettingsService } from './global-notifications-settings.service';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AlertService } from 'app/shared/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import * as globalUtils from 'app/shared/util/global.utils';
import { RouterLink } from '@angular/router';

describe('GlobalNotificationsSettingsComponent', () => {
    let component: GlobalNotificationsSettingsComponent;
    let fixture: ComponentFixture<GlobalNotificationsSettingsComponent>;
    let alertService: AlertService;

    const mockService = {
        getAll: jest.fn(),
        update: jest.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, GlobalNotificationsSettingsComponent],
            declarations: [MockDirective(TranslateDirective), MockPipe(ArtemisDatePipe), MockDirective(RouterLink)],
            providers: [
                { provide: GlobalNotificationSettingsService, useValue: mockService },
                MockProvider(ProfileService, { isModuleFeatureActive: jest.fn().mockReturnValue(true) }),
                MockProvider(AlertService),
            ],
            schemas: [CUSTOM_ELEMENTS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(GlobalNotificationsSettingsComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    const mockSettings = {
        [GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN]: true,
        [GLOBAL_NOTIFICATION_TYPES.NEW_PASSKEY_ADDED]: false,
        [GLOBAL_NOTIFICATION_TYPES.VCS_TOKEN_EXPIRED]: true,
        [GLOBAL_NOTIFICATION_TYPES.SSH_KEY_EXPIRED]: false,
    };

    it('should load notification settings on init', fakeAsync(() => {
        mockService.getAll.mockReturnValue(of(mockSettings));

        component.ngOnInit();
        tick();

        expect(mockService.getAll).toHaveBeenCalledOnce();
        expect(component.notificationSettings).toEqual(mockSettings);
    }));

    it('should update a notification setting', fakeAsync(() => {
        mockService.update.mockReturnValue(of({}));
        component.notificationSettings = { ...mockSettings };

        component.updateSetting(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN, false);
        tick();

        expect(mockService.update).toHaveBeenCalledWith(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN, false);
        expect(component.notificationSettings?.[GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN]).toBeFalse();
    }));

    it('should generate the correct i18n label key', () => {
        const labelKey = component.getNotificationTypeLabel(GLOBAL_NOTIFICATION_TYPES.SSH_KEY_EXPIRED);
        expect(labelKey).toBe('artemisApp.userSettings.globalNotificationSettings.options.SSH_KEY_EXPIRED');
    });

    it('should handle error when loading settings', () => {
        const error = new Error('Failed to load');
        jest.spyOn(globalUtils, 'onError');
        mockService.getAll.mockReturnValue(throwError(() => error));

        component.loadSettings();

        expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
    });

    it('should handle error when updating setting', fakeAsync(() => {
        const error = new Error('Update failed');
        jest.spyOn(globalUtils, 'onError');
        mockService.update.mockReturnValue(throwError(() => error));

        component.notificationSettings = { ...mockSettings };
        component.updateSetting(GLOBAL_NOTIFICATION_TYPES.VCS_TOKEN_EXPIRED, false);
        tick();

        expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
    }));

    describe('isSettingAvailable', () => {
        it('should return true for types other than NEW_PASSKEY_ADDED', () => {
            expect(component.isSettingAvailable(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN)).toBeTrue();
            expect(component.isSettingAvailable(GLOBAL_NOTIFICATION_TYPES.SSH_KEY_EXPIRED)).toBeTrue();
        });

        it('should return true for NEW_PASSKEY_ADDED if passkey is enabled', () => {
            component.isPasskeyEnabled = true;
            expect(component.isSettingAvailable(GLOBAL_NOTIFICATION_TYPES.NEW_PASSKEY_ADDED)).toBeTrue();
        });

        it('should return false for NEW_PASSKEY_ADDED if passkey is disabled', () => {
            component.isPasskeyEnabled = false;
            expect(component.isSettingAvailable(GLOBAL_NOTIFICATION_TYPES.NEW_PASSKEY_ADDED)).toBeFalse();
        });

        it('should render correct links for special notification types', fakeAsync(() => {
            mockService.getAll.mockReturnValue(of(mockSettings));

            component.ngOnInit();
            tick();
            fixture.detectChanges();

            const element: HTMLElement = fixture.nativeElement;
            const links = element.querySelectorAll('a.small');

            const routerLinks = Array.from(links).map((link) => link.getAttribute('ng-reflect-router-link'));

            expect(routerLinks).toContain('/user-settings,passkeys');
            expect(routerLinks).toContain('/user-settings,vcs-token');
            expect(routerLinks).toContain('/user-settings,ssh');

            const linkSpans = Array.from(links).map((link) => link.querySelector('span')?.getAttribute('jhiTranslate'));
            expect(linkSpans).toContain('artemisApp.userSettings.globalNotificationSettings.viewPasskeySettings');
            expect(linkSpans).toContain('artemisApp.userSettings.globalNotificationSettings.viewVcsTokenSettings');
            expect(linkSpans).toContain('artemisApp.userSettings.globalNotificationSettings.viewSshKeySettings');
        }));
    });
});
