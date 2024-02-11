import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockPipe, MockProvider } from 'ng-mocks';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { AlertService } from 'app/core/util/alert.service';
import { UrlSerializer } from '@angular/router';
import { ScienceSettingsComponent } from 'app/shared/user-settings/science-settings/science-settings.component';
import { ScienceSettingsService } from 'app/shared/user-settings/science-settings/science-settings.service';
import { ScienceSetting, scienceSettingsStructure } from 'app/shared/user-settings/science-settings/science-settings-structure';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';

describe('ScienceSettingsComponent', () => {
    let comp: ScienceSettingsComponent;
    let fixture: ComponentFixture<ScienceSettingsComponent>;

    let scienceSettingsServiceMock: ScienceSettingsService;

    const settingId = SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING;
    const activeStatus = false;
    const scienceSetting: ScienceSetting = {
        settingId,
        active: activeStatus,
        changed: false,
    };

    const imports = [ArtemisTestModule];
    const declarations = [ScienceSettingsComponent, MockHasAnyAuthorityDirective, MockPipe(ArtemisTranslatePipe)];
    const providers = [
        MockProvider(AlertService),
        MockProvider(ScienceSettingsService),
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
                fixture = TestBed.createComponent(ScienceSettingsComponent);
                comp = fixture.componentInstance;
                scienceSettingsServiceMock = TestBed.inject(ScienceSettingsService);
            });
    });

    it('should toggle setting', () => {
        comp.settings = [scienceSetting];
        const event = {
            currentTarget: {
                id: settingId,
            },
        };
        expect(comp.settingsChanged).toBeFalse();
        expect(scienceSetting.changed).toBeFalse();

        comp.toggleSetting(event);

        expect(scienceSetting.active).not.toEqual(activeStatus);
        expect(scienceSetting.changed).toBeTrue();
        expect(comp.settingsChanged).toBeTrue();
    });

    it('should reuse settings via service if they were already loaded', () => {
        const settingGetMock = jest.spyOn(scienceSettingsServiceMock, 'getScienceSettings').mockReturnValue([scienceSetting]);
        comp.ngOnInit();
        expect(settingGetMock).toHaveBeenCalledOnce();
        // check if current settings are not empty
        expect(comp.userSettings).toEqual(scienceSettingsStructure);
    });
});
