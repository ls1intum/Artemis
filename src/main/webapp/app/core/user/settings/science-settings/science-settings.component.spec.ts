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
import { provideHttpClient } from '@angular/common/http';
import { ScienceSettingsComponent } from 'app/core/user/settings/science-settings/science-settings.component';
import { ScienceSettingsService } from 'app/core/user/settings/science-settings/science-settings.service';
import { ScienceSetting, scienceSettingsStructure } from 'app/core/user/settings/science-settings/science-settings-structure';

describe('ScienceSettingsComponent', () => {
    setupTestBed({ zoneless: true });

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
        TestBed.configureTestingModule({
            imports: [ScienceSettingsComponent, MockHasAnyAuthorityDirective, MockPipe(ArtemisTranslatePipe)],
            providers,
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(ScienceSettingsComponent);
        comp = fixture.componentInstance;
        scienceSettingsServiceMock = TestBed.inject(ScienceSettingsService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should toggle setting', () => {
        comp.settings = [scienceSetting];
        const event = {
            currentTarget: {
                id: settingId,
            },
        };
        expect(comp.settingsChanged).toBe(false);
        expect(scienceSetting.changed).toBe(false);

        comp.toggleSetting(event);

        expect(scienceSetting.active).not.toEqual(activeStatus);
        expect(scienceSetting.changed).toBe(true);
        expect(comp.settingsChanged).toBe(true);
    });

    it('should reuse settings via service if they were already loaded', () => {
        const settingGetMock = vi.spyOn(scienceSettingsServiceMock, 'getScienceSettings').mockReturnValue([scienceSetting]);
        comp.ngOnInit();
        expect(settingGetMock).toHaveBeenCalledOnce();
        // check if current settings are not empty
        expect(comp.userSettings).toEqual(scienceSettingsStructure);
    });
});
