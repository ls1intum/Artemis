import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('GlobalNotificationsSettingsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GlobalNotificationsSettingsComponent;
    let fixture: ComponentFixture<GlobalNotificationsSettingsComponent>;
    let alertService: AlertService;

    const mockService = {
        getAll: vi.fn(),
        update: vi.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, GlobalNotificationsSettingsComponent, MockDirective(TranslateDirective), MockPipe(ArtemisDatePipe), MockDirective(RouterLink)],
            providers: [
                { provide: GlobalNotificationSettingsService, useValue: mockService },
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

    it('should load notification settings on init', async () => {
        mockService.getAll.mockReturnValue(of(mockSettings));

        component.ngOnInit();
        await firstValueFrom(mockService.getAll());

        expect(mockService.getAll).toHaveBeenCalled();
        expect(component.notificationSettings).toEqual(mockSettings);
    });

    it('should update a notification setting', async () => {
        mockService.update.mockReturnValue(of({}));
        component.notificationSettings = { ...mockSettings };

        component.updateSetting(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN, false);
        await firstValueFrom(mockService.update());

        expect(mockService.update).toHaveBeenCalledWith(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN, false);
        expect(component.notificationSettings?.[GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN]).toBeFalsy();
    });

    it('should generate the correct i18n label key', () => {
        const labelKey = component.getNotificationTypeLabel(GLOBAL_NOTIFICATION_TYPES.SSH_KEY_EXPIRED);
        expect(labelKey).toBe('artemisApp.userSettings.globalNotificationSettings.options.SSH_KEY_EXPIRED');
    });

    it('should handle error when loading settings', () => {
        const error = new Error('Failed to load');
        vi.spyOn(globalUtils, 'onError');
        mockService.getAll.mockReturnValue(throwError(() => error));

        component.loadSettings();

        expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
    });

    it('should handle error when updating setting', async () => {
        const error = new Error('Update failed');
        vi.spyOn(globalUtils, 'onError');
        mockService.update.mockReturnValue(throwError(() => error));

        component.notificationSettings = { ...mockSettings };
        component.updateSetting(GLOBAL_NOTIFICATION_TYPES.VCS_TOKEN_EXPIRED, false);
        await vi.waitFor(() => {
            expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
        });
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

        it('should render correct links for special notification types', async () => {
            mockService.getAll.mockReturnValue(of(mockSettings));

            component.ngOnInit();
            await firstValueFrom(mockService.getAll());
            fixture.detectChanges();

            const anchorElements = fixture.debugElement.queryAll(By.directive(RouterLink));
            // Verify that router link elements are rendered for each notification type link
            expect(anchorElements.length).toBeGreaterThanOrEqual(component.notificationTypeLinks.length);
        });
    });
});
