import { NotificationSettingsCommunicationChannel, NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockPipe, MockProvider } from 'ng-mocks';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { NotificationSetting, notificationSettingsStructure } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { AlertService } from 'app/shared/service/alert.service';
import { UrlSerializer } from '@angular/router';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';

describe('NotificationSettingsComponent', () => {
    let comp: NotificationSettingsComponent;
    let fixture: ComponentFixture<NotificationSettingsComponent>;

    let notificationSettingsServiceMock: NotificationSettingsService;

    const settingId = SettingId.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES;
    const webappStatus = true;
    const notificationSettingA: NotificationSetting = {
        settingId,
        webapp: webappStatus,
        email: false,
        changed: false,
    };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [NotificationSettingsComponent, MockHasAnyAuthorityDirective, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(AlertService),
                MockProvider(NotificationSettingsService),
                MockProvider(UrlSerializer),
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(NotificationSettingsComponent);
                comp = fixture.componentInstance;
                notificationSettingsServiceMock = TestBed.inject(NotificationSettingsService);
            });
    });

    it('should toggle setting', () => {
        comp.settings = [notificationSettingA];
        const event = {
            currentTarget: {
                id: settingId,
            },
        };
        expect(comp.settingsChanged).toBeFalse();
        expect(notificationSettingA.changed).toBeFalse();

        comp.toggleSetting(event, NotificationSettingsCommunicationChannel.WEBAPP);

        expect(notificationSettingA.webapp).not.toEqual(webappStatus);
        expect(notificationSettingA.changed).toBeTrue();
        expect(comp.settingsChanged).toBeTrue();
    });

    it('should reuse settings via service if they were already loaded', () => {
        const settingGetMock = jest.spyOn(notificationSettingsServiceMock, 'getNotificationSettings').mockReturnValue([notificationSettingA]);
        comp.ngOnInit();
        expect(settingGetMock).toHaveBeenCalledOnce();
        // check if current settings are not empty
        expect(comp.userSettings).toEqual(notificationSettingsStructure);
    });
});
