import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { Subject, of, throwError } from 'rxjs';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { GLOBAL_NOTIFICATION_TYPES, GlobalNotificationsSettingsComponent } from './global-notifications-settings.component';
import { GlobalNotificationSettingApi } from 'app/openapi/api/global-notification-setting-api';
import { GlobalNotificationSetting } from 'app/openapi/model/global-notification-setting';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import * as globalUtils from 'app/foundation/util/global.utils';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('GlobalNotificationsSettingsComponent', () => {
    let component: GlobalNotificationsSettingsComponent;
    let fixture: ComponentFixture<GlobalNotificationsSettingsComponent>;
    let alertService: AlertService;

    const mockService = {
        getGlobalNotificationSettings: vi.fn<GlobalNotificationSettingApi['getGlobalNotificationSettings']>(),
        updateGlobalNotificationSetting: vi.fn<GlobalNotificationSettingApi['updateGlobalNotificationSetting']>(),
    };

    beforeEach(async () => {
        mockService.getGlobalNotificationSettings.mockReset();
        mockService.updateGlobalNotificationSetting.mockReset();
        await TestBed.configureTestingModule({
            imports: [FormsModule, GlobalNotificationsSettingsComponent, MockDirective(TranslateDirective), MockPipe(ArtemisDatePipe), MockDirective(RouterLink)],
            providers: [
                { provide: GlobalNotificationSettingApi, useValue: mockService },
                MockProvider(ProfileService, { isModuleFeatureActive: vi.fn().mockReturnValue(true) }),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ActivatedRoute),
            ],
            schemas: [CUSTOM_ELEMENTS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(GlobalNotificationsSettingsComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const mockSettings = {
        [GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN]: true,
        [GLOBAL_NOTIFICATION_TYPES.NEW_PASSKEY_ADDED]: false,
        [GLOBAL_NOTIFICATION_TYPES.VCS_TOKEN_EXPIRED]: true,
        [GLOBAL_NOTIFICATION_TYPES.SSH_KEY_EXPIRED]: false,
    };

    const updatedSetting: GlobalNotificationSetting = { id: 1, userId: 2, notificationType: 'NEW_LOGIN', enabled: false };

    it('should leave settings undefined until a response arrives', () => {
        const pending = new Subject<Record<string, boolean>>();
        mockService.getGlobalNotificationSettings.mockReturnValue(pending);

        component.ngOnInit();

        expect(component.notificationSettings()).toBeUndefined();
        pending.next(mockSettings);
        expect(component.notificationSettings()).toEqual(mockSettings);
    });

    it('should cancel a superseded load and ignore its response', () => {
        const first = new Subject<Record<string, boolean>>();
        const second = new Subject<Record<string, boolean>>();
        mockService.getGlobalNotificationSettings.mockReturnValueOnce(first).mockReturnValueOnce(second);

        component.loadSettings();
        component.loadSettings();
        first.next({ NEW_LOGIN: false });

        expect(first.observed).toBe(false);
        expect(component.notificationSettings()).toBeUndefined();
        second.next(mockSettings);
        expect(component.notificationSettings()).toEqual(mockSettings);
    });

    it('should cancel an earlier save without applying its result', () => {
        const first = new Subject<GlobalNotificationSetting>();
        const second = new Subject<GlobalNotificationSetting>();
        mockService.updateGlobalNotificationSetting.mockReturnValueOnce(first).mockReturnValueOnce(second);
        component.notificationSettings.set(mockSettings);

        component.updateSetting('NEW_LOGIN', false);
        component.updateSetting('NEW_LOGIN', true);
        first.next(updatedSetting);

        expect(first.observed).toBe(false);
        expect(alertService.success).not.toHaveBeenCalled();
        second.next({ ...updatedSetting, enabled: true });
        expect(component.notificationSettings()?.NEW_LOGIN).toBe(true);
        expect(alertService.success).toHaveBeenCalledExactlyOnceWith('artemisApp.userSettings.globalNotificationSettings.updateSuccess');
    });

    it('should cancel pending loads and saves when destroyed', () => {
        const load = new Subject<Record<string, boolean>>();
        const save = new Subject<GlobalNotificationSetting>();
        mockService.getGlobalNotificationSettings.mockReturnValue(load);
        mockService.updateGlobalNotificationSetting.mockReturnValue(save);
        component.loadSettings();
        component.updateSetting('NEW_LOGIN', false);

        component.ngOnDestroy();

        expect(load.observed).toBe(false);
        expect(save.observed).toBe(false);
    });

    it('should hide the passkey setting when the module feature is disabled', () => {
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(false);
        mockService.getGlobalNotificationSettings.mockReturnValue(of(mockSettings));

        component.ngOnInit();

        expect(component['filteredNotificationTypes']()).not.toContain('NEW_PASSKEY_ADDED');
        expect(component['filteredNotificationTypes']()).toHaveLength(7);
    });

    it('should load notification settings on init', () => {
        mockService.getGlobalNotificationSettings.mockReturnValue(of(mockSettings));

        component.ngOnInit();

        expect(mockService.getGlobalNotificationSettings).toHaveBeenCalled();
        expect(component.notificationSettings()).toEqual(mockSettings);
    });

    it('should update a notification setting', () => {
        mockService.updateGlobalNotificationSetting.mockReturnValue(of(updatedSetting));
        component.notificationSettings.set({ ...mockSettings });

        component.updateSetting(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN, false);

        expect(mockService.updateGlobalNotificationSetting).toHaveBeenCalledWith(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN, { enabled: false });
        expect(component.notificationSettings()?.[GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN]).toBe(false);
        expect(mockSettings.NEW_LOGIN).toBe(true);
        expect(alertService.success).toHaveBeenCalledExactlyOnceWith('artemisApp.userSettings.globalNotificationSettings.updateSuccess');
    });

    it('should generate the correct i18n label key', () => {
        const labelKey = component.getNotificationTypeLabel(GLOBAL_NOTIFICATION_TYPES.SSH_KEY_EXPIRED);
        expect(labelKey).toBe('artemisApp.userSettings.globalNotificationSettings.options.SSH_KEY_EXPIRED');
    });

    it('should handle error when loading settings', () => {
        const error = new Error('Failed to load');
        vi.spyOn(globalUtils, 'onError');
        mockService.getGlobalNotificationSettings.mockReturnValue(throwError(() => error));

        component.loadSettings();

        expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
    });

    it('should handle error when updating setting', async () => {
        const error = new Error('Update failed');
        vi.spyOn(globalUtils, 'onError');
        mockService.updateGlobalNotificationSetting.mockReturnValue(throwError(() => error));

        component.notificationSettings.set({ ...mockSettings });
        component.updateSetting(GLOBAL_NOTIFICATION_TYPES.VCS_TOKEN_EXPIRED, false);
        await vi.waitFor(() => {
            expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
        });
        expect(component.notificationSettings()).toEqual(mockSettings);
        expect(alertService.success).not.toHaveBeenCalled();
    });

    describe('isSettingAvailable', () => {
        it('should return true for types other than NEW_PASSKEY_ADDED', () => {
            expect(component.isSettingAvailable(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN)).toBeTruthy();
            expect(component.isSettingAvailable(GLOBAL_NOTIFICATION_TYPES.SSH_KEY_EXPIRED)).toBeTruthy();
        });

        it('should return true for NEW_PASSKEY_ADDED if passkey is enabled', () => {
            component.isPasskeyEnabled = true;
            expect(component.isSettingAvailable(GLOBAL_NOTIFICATION_TYPES.NEW_PASSKEY_ADDED)).toBeTruthy();
        });

        it('should return false for NEW_PASSKEY_ADDED if passkey is disabled', () => {
            component.isPasskeyEnabled = false;
            expect(component.isSettingAvailable(GLOBAL_NOTIFICATION_TYPES.NEW_PASSKEY_ADDED)).toBeFalsy();
        });
    });

    describe('notificationTypeLinks', () => {
        it('should have correct configuration for all notification types with links', () => {
            const links = component.notificationTypeLinks;
            expect(links).toHaveLength(3);

            const passkeyLink = links.find((link) => link.type === GLOBAL_NOTIFICATION_TYPES.NEW_PASSKEY_ADDED);
            expect(passkeyLink).toBeDefined();
            expect(passkeyLink?.routerLink).toEqual(['/user-settings', 'passkeys']);
            expect(passkeyLink?.translationKey).toBe('artemisApp.userSettings.globalNotificationSettings.viewPasskeySettings');

            const vcsTokenLink = links.find((link) => link.type === GLOBAL_NOTIFICATION_TYPES.VCS_TOKEN_EXPIRED);
            expect(vcsTokenLink).toBeDefined();
            expect(vcsTokenLink?.routerLink).toEqual(['/user-settings', 'vcs-token']);
            expect(vcsTokenLink?.translationKey).toBe('artemisApp.userSettings.globalNotificationSettings.viewVcsTokenSettings');

            const sshKeyLink = links.find((link) => link.type === GLOBAL_NOTIFICATION_TYPES.SSH_KEY_EXPIRED);
            expect(sshKeyLink).toBeDefined();
            expect(sshKeyLink?.routerLink).toEqual(['/user-settings', 'ssh']);
            expect(sshKeyLink?.translationKey).toBe('artemisApp.userSettings.globalNotificationSettings.viewSshKeySettings');
        });

        it('should render correct links for special notification types', () => {
            mockService.getGlobalNotificationSettings.mockReturnValue(of(mockSettings));

            component.ngOnInit();
            fixture.detectChanges();

            const anchorElements = fixture.debugElement.queryAll(By.directive(RouterLink));
            // Verify that router link elements are rendered for each notification type link
            expect(anchorElements.length).toBeGreaterThanOrEqual(component.notificationTypeLinks.length);
        });
    });
});
