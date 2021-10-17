import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockProvider } from 'ng-mocks/cjs/lib/mock-provider/mock-provider';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockPipe } from 'ng-mocks/cjs/lib/mock-pipe/mock-pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { MockComponent } from 'ng-mocks';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { AlertService } from 'app/core/util/alert.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../helpers/mocks/service/mock-metis-service.service';

describe('NotificationSettingsComponent', () => {
    let comp: NotificationSettingsComponent;
    let fixture: ComponentFixture<NotificationSettingsComponent>;

    const imports = [ArtemisTestModule];
    const declarations = [MockComponent(AlertComponent), NotificationSettingsComponent, MockHasAnyAuthorityDirective, MockPipe(ArtemisTranslatePipe)];
    const providers = [
        MockProvider(AlertService),
        { provide: LocalStorageService, useClass: MockSyncStorage },
        { provide: SessionStorageService, useClass: MockSyncStorage },
        { provide: MetisService, useClass: MockMetisService },
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
            });
    });

    it('should toggle setting', () => {
        const settingId = SettingId.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES;
        const webappStatus = true;
        const notificationSettingA: NotificationSetting = {
            settingId,
            webapp: webappStatus,
            email: false,
            changed: false,
        };
        comp.settings = [notificationSettingA];
        const event = {
            currentTarget: {
                id: settingId,
            },
        };
        expect(comp.settingsChanged).toBe(false);
        expect(notificationSettingA.changed).toBe(false);

        comp.toggleSetting(event);

        expect(notificationSettingA.webapp).not.toEqual(webappStatus);
        expect(notificationSettingA.changed).toBe(true);
        expect(comp.settingsChanged).toBe(true);
    });
});
