import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UrlSerializer } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NotificationSetting, notificationSettingsStructure } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { NotificationSettingsCommunicationChannel, NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../../test.module';

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

    const imports = [ArtemisTestModule];
    const declarations = [NotificationSettingsComponent, MockHasAnyAuthorityDirective, MockPipe(ArtemisTranslatePipe)];
    const providers = [
        MockProvider(AlertService),
        MockProvider(NotificationSettingsService),
        MockProvider(UrlSerializer),
        { provide: LocalStorageService, useClass: MockSyncStorage },
        { provide: SessionStorageService, useClass: MockSyncStorage },
    ];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports,
            declarations,
            providers,
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
