import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { GlobalNotificationsSettingsComponent } from './global-notifications-settings.component';
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
        NEW_LOGIN: true,
        NEW_PASSKEY_ADDED: false,
        VCS_TOKEN_EXPIRED: true,
        SSH_KEY_EXPIRED: false,
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

        component.updateSetting('NEW_LOGIN', false);
        tick();

        expect(mockService.update).toHaveBeenCalledWith('NEW_LOGIN', false);
        expect(component.notificationSettings?.NEW_LOGIN).toBeFalse();
    }));

    it('should generate the correct i18n label key', () => {
        const labelKey = component.getNotificationTypeLabel('SSH_KEY_EXPIRED');
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
        component.updateSetting('VCS_TOKEN_EXPIRED', false);
        tick();

        expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
    }));

    describe('isSettingAvailable', () => {
        it('should return true for types other than NEW_PASSKEY_ADDED', () => {
            expect(component.isSettingAvailable('NEW_LOGIN')).toBeTrue();
            expect(component.isSettingAvailable('SSH_KEY_EXPIRED')).toBeTrue();
        });

        it('should return true for NEW_PASSKEY_ADDED if passkey is enabled', () => {
            component.isPasskeyEnabled = true;
            expect(component.isSettingAvailable('NEW_PASSKEY_ADDED')).toBeTrue();
        });

        it('should return false for NEW_PASSKEY_ADDED if passkey is disabled', () => {
            component.isPasskeyEnabled = false;
            expect(component.isSettingAvailable('NEW_PASSKEY_ADDED')).toBeFalse();
        });
    });
});
