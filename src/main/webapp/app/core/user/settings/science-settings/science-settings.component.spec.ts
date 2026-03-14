import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ScienceSettingsComponent } from 'app/core/user/settings/science-settings/science-settings.component';
import { ScienceSettingsService } from 'app/core/user/settings/science-settings/science-settings.service';
import { ScienceSetting, scienceSettingsStructure } from 'app/core/user/settings/science-settings/science-settings-structure';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { of, throwError } from 'rxjs';

describe('ScienceSettingsComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ScienceSettingsComponent;
    let fixture: ComponentFixture<ScienceSettingsComponent>;

    let scienceSettingsServiceMock: ScienceSettingsService;
    let userSettingsServiceMock: UserSettingsService;

    const settingId = SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING;
    const activeStatus = false;

    let scienceSetting: ScienceSetting;

    const providers = [
        MockProvider(AlertService),
        MockProvider(ScienceSettingsService),
        MockProvider(UrlSerializer),
        LocalStorageService,
        SessionStorageService,
        { provide: TranslateService, useClass: MockTranslateService },
        provideHttpClient(),
    ];

    beforeEach(async () => {
        scienceSetting = {
            settingId,
            active: activeStatus,
            changed: false,
        };

        TestBed.configureTestingModule({
            imports: [ScienceSettingsComponent, MockHasAnyAuthorityDirective, MockPipe(ArtemisTranslatePipe)],
            providers,
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(ScienceSettingsComponent);
        comp = fixture.componentInstance;
        scienceSettingsServiceMock = TestBed.inject(ScienceSettingsService);
        userSettingsServiceMock = TestBed.inject(UserSettingsService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should toggle setting and save immediately', () => {
        comp.settings = [scienceSetting];
        const saveResponse = new HttpResponse<ScienceSetting[]>({ body: [{ ...scienceSetting, active: true, changed: false }] });
        vi.spyOn(userSettingsServiceMock, 'saveSettings').mockReturnValue(of(saveResponse));
        vi.spyOn(userSettingsServiceMock, 'saveSettingsSuccess').mockReturnValue(scienceSettingsStructure);
        vi.spyOn(userSettingsServiceMock, 'extractIndividualSettingsFromSettingsStructure').mockReturnValue([scienceSetting]);
        const event = { currentTarget: { id: settingId } } as unknown as MouseEvent;

        comp.toggleSetting(event);

        expect(scienceSetting.active).not.toEqual(activeStatus);
        expect(scienceSetting.changed).toBe(true);
        expect(userSettingsServiceMock.saveSettings).toHaveBeenCalledOnce();
    });

    it('should revert toggle on save failure', () => {
        comp.settings = [scienceSetting];
        const errorResponse = new HttpErrorResponse({ error: { message: 'Save failed' }, status: 500 });
        vi.spyOn(userSettingsServiceMock, 'saveSettings').mockReturnValue(throwError(() => errorResponse));
        const event = { currentTarget: { id: settingId } } as unknown as MouseEvent;

        comp.toggleSetting(event);

        expect(scienceSetting.active).toEqual(activeStatus);
        expect(scienceSetting.changed).toBe(false);
    });

    it('should not save when setting ID is not found', () => {
        comp.settings = [scienceSetting];
        const saveSpy = vi.spyOn(userSettingsServiceMock, 'saveSettings');
        const event = { currentTarget: { id: 'NON_EXISTENT_ID' } } as unknown as MouseEvent;

        comp.toggleSetting(event);

        expect(saveSpy).not.toHaveBeenCalled();
        expect(scienceSetting.active).toEqual(activeStatus);
    });

    it('should reuse settings via service if they were already loaded', () => {
        const settingGetMock = vi.spyOn(scienceSettingsServiceMock, 'getScienceSettings').mockReturnValue([scienceSetting]);
        comp.ngOnInit();
        expect(settingGetMock).toHaveBeenCalledOnce();
        // check if current settings are not empty
        expect(comp.userSettings).toEqual(scienceSettingsStructure);
    });
});
