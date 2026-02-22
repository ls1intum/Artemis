import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockPipe, MockProvider } from 'ng-mocks';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { AlertService } from 'app/shared/service/alert.service';
import { UrlSerializer } from '@angular/router';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { ScienceSettingsComponent } from 'app/core/user/settings/science-settings/science-settings.component';
import { ScienceSettingsService } from 'app/core/user/settings/science-settings/science-settings.service';
import { ScienceSetting, scienceSettingsStructure } from 'app/core/user/settings/science-settings/science-settings-structure';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

describe('ScienceSettingsComponent', () => {
    let comp: ScienceSettingsComponent;
    let fixture: ComponentFixture<ScienceSettingsComponent>;

    let scienceSettingsServiceMock: ScienceSettingsService;
    let userSettingsServiceMock: UserSettingsService;

    const settingId = SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING;
    const activeStatus = false;

    let scienceSetting: ScienceSetting;

    const declarations = [ScienceSettingsComponent, MockHasAnyAuthorityDirective, MockPipe(ArtemisTranslatePipe)];
    const providers = [
        MockProvider(AlertService),
        MockProvider(ScienceSettingsService),
        MockProvider(UrlSerializer),
        LocalStorageService,
        SessionStorageService,
        { provide: TranslateService, useClass: MockTranslateService },
        provideHttpClient(),
    ];

    beforeEach(() => {
        scienceSetting = {
            settingId,
            active: activeStatus,
            changed: false,
        };

        return TestBed.configureTestingModule({
            declarations,
            providers,
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ScienceSettingsComponent);
                comp = fixture.componentInstance;
                scienceSettingsServiceMock = TestBed.inject(ScienceSettingsService);
                userSettingsServiceMock = TestBed.inject(UserSettingsService);
            });
    });

    it('should toggle setting and save immediately', () => {
        comp.settings = [scienceSetting];
        const saveResponse = new HttpResponse<ScienceSetting[]>({ body: [{ ...scienceSetting, active: true, changed: false }] });
        jest.spyOn(userSettingsServiceMock, 'saveSettings').mockReturnValue(of(saveResponse));
        jest.spyOn(userSettingsServiceMock, 'saveSettingsSuccess').mockReturnValue(comp.userSettings);
        jest.spyOn(userSettingsServiceMock, 'extractIndividualSettingsFromSettingsStructure').mockReturnValue(comp.settings);
        const event = { currentTarget: { id: settingId } } as unknown as MouseEvent;

        comp.toggleSetting(event);

        expect(scienceSetting.active).not.toEqual(activeStatus);
        expect(scienceSetting.changed).toBeTrue();
        expect(userSettingsServiceMock.saveSettings).toHaveBeenCalledOnce();
    });

    it('should revert toggle on save failure', () => {
        comp.settings = [scienceSetting];
        const errorResponse = new HttpErrorResponse({ error: { message: 'Save failed' }, status: 500 });
        jest.spyOn(userSettingsServiceMock, 'saveSettings').mockReturnValue(throwError(() => errorResponse));
        const event = { currentTarget: { id: settingId } } as unknown as MouseEvent;

        comp.toggleSetting(event);

        expect(scienceSetting.active).toEqual(activeStatus);
        expect(scienceSetting.changed).toBeFalse();
    });

    it('should not save when setting ID is not found', () => {
        comp.settings = [scienceSetting];
        const saveSpy = jest.spyOn(userSettingsServiceMock, 'saveSettings');
        const event = { currentTarget: { id: 'NON_EXISTENT_ID' } } as unknown as MouseEvent;

        comp.toggleSetting(event);

        expect(saveSpy).not.toHaveBeenCalled();
        expect(scienceSetting.active).toEqual(activeStatus);
    });

    it('should reuse settings via service if they were already loaded', () => {
        const settingGetMock = jest.spyOn(scienceSettingsServiceMock, 'getScienceSettings').mockReturnValue([scienceSetting]);
        comp.ngOnInit();
        expect(settingGetMock).toHaveBeenCalledOnce();
        // check if current settings are not empty
        expect(comp.userSettings).toEqual(scienceSettingsStructure);
    });
});
