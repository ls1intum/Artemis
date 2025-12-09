import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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

        expect(mockService.getAll).toHaveBeenCalled();
        expect(component.notificationSettings).toEqual(mockSettings);
    }));

    it('should update a notification setting', fakeAsync(() => {
        mockService.update.mockReturnValue(of({}));
        component.notificationSettings = Object.assign({}, mockSettings);

        component.updateSetting(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN, false);
        tick();

        expect(mockService.update).toHaveBeenCalledWith(GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN, false);
        expect(component.notificationSettings?.[GLOBAL_NOTIFICATION_TYPES.NEW_LOGIN]).toBeFalsy();
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

        component.notificationSettings = Object.assign({}, mockSettings);
        component.updateSetting(GLOBAL_NOTIFICATION_TYPES.VCS_TOKEN_EXPIRED, false);
        tick();

        expect(globalUtils.onError).toHaveBeenCalledWith(alertService, error);
    }));

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

        it('should render correct links for special notification types', fakeAsync(() => {
            mockService.getAll.mockReturnValue(of(mockSettings));

            component.ngOnInit();
            tick();
            fixture.detectChanges();

            const anchorElements = fixture.debugElement.queryAll(By.directive(RouterLink));
            const actualRouterLinks = anchorElements.map((debugElement) => (debugElement.injector.get(RouterLink).routerLink as string[]).join('/'));
            const expectedRouterLinks = component.notificationTypeLinks.map((link) => link.routerLink.join('/'));

            expectedRouterLinks.forEach((expected) => {
                const found = actualRouterLinks.some((actual) => actual === expected);
                expect(found).toBeTrue();
            });
        }));
    });
});
