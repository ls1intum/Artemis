import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { effect } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockPipe, MockProvider } from 'ng-mocks';
import { SettingId } from 'app/foundation/constants/user-settings.constants';
import { AlertService } from 'app/foundation/service/alert.service';
import { UrlSerializer } from '@angular/router';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ScienceSettingsComponent } from 'app/account/user/settings/science-settings/science-settings.component';
import { ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { ScienceSetting, scienceSettingsStructure } from 'app/account/user/settings/science-settings/science-settings-structure';
import { UserSettingsService } from 'app/account/user/settings/directive/user-settings.service';
import { of, throwError } from 'rxjs';
import { deepClone } from 'app/foundation/util/deep-clone.util';

describe('ScienceSettingsComponent', () => {
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
        comp.settings.set([scienceSetting]);
        const saveResponse = new HttpResponse<ScienceSetting[]>({ body: [{ ...scienceSetting, active: true, changed: false }] });
        vi.spyOn(userSettingsServiceMock, 'saveSettings').mockReturnValue(of(saveResponse));
        vi.spyOn(userSettingsServiceMock, 'saveSettingsSuccess').mockReturnValue(scienceSettingsStructure);
        vi.spyOn(userSettingsServiceMock, 'extractIndividualSettingsFromSettingsStructure').mockReturnValue([scienceSetting]);
        comp.toggleSetting(scienceSetting, !activeStatus);

        expect(scienceSetting.active).not.toEqual(activeStatus);
        expect(scienceSetting.changed).toBe(true);
        expect(userSettingsServiceMock.saveSettings).toHaveBeenCalledOnce();
    });

    it('should notify the settings structure after reverting a failed save', () => {
        // The revert mutates the setting in place, so the structure signal has to notify on the same
        // reference; otherwise the rendered switch keeps the value the user optimistically toggled to.
        const structure = deepClone(scienceSettingsStructure);
        const setting = structure.groups[0].settings[0];
        setting.active = false;
        comp.userSettings.set(structure);
        comp.settings.set([setting]);

        const errorResponse = new HttpErrorResponse({ error: { message: 'Save failed' }, status: 500 });
        vi.spyOn(userSettingsServiceMock, 'saveSettings').mockReturnValue(throwError(() => errorResponse));
        // Change detection runs ngOnInit, which reads the already-loaded settings rather than fetching them.
        vi.spyOn(scienceSettingsServiceMock, 'getScienceSettings').mockReturnValue([setting]);
        vi.spyOn(userSettingsServiceMock, 'loadSettingsSuccessAsSettingsStructure').mockReturnValue(structure);
        vi.spyOn(userSettingsServiceMock, 'extractIndividualSettingsFromSettingsStructure').mockReturnValue([setting]);

        let notifications = 0;
        TestBed.runInInjectionContext(() => {
            effect(() => {
                comp.userSettings();
                notifications++;
            });
        });
        TestBed.tick();
        const before = notifications;

        comp.toggleSetting(setting, true);
        TestBed.tick();

        expect(setting.active).toBe(false);
        expect(notifications).toBeGreaterThan(before);
    });

    it('should revert toggle on save failure', () => {
        comp.settings.set([scienceSetting]);
        const errorResponse = new HttpErrorResponse({ error: { message: 'Save failed' }, status: 500 });
        vi.spyOn(userSettingsServiceMock, 'saveSettings').mockReturnValue(throwError(() => errorResponse));
        comp.toggleSetting(scienceSetting, !activeStatus);

        expect(scienceSetting.active).toEqual(activeStatus);
        expect(scienceSetting.changed).toBe(false);
    });

    it('should not save when setting ID is not found', () => {
        comp.settings.set([scienceSetting]);
        const saveSpy = vi.spyOn(userSettingsServiceMock, 'saveSettings');
        comp.toggleSetting({ ...scienceSetting, settingId: 'NON_EXISTENT_ID' as ScienceSetting['settingId'] }, true);

        expect(saveSpy).not.toHaveBeenCalled();
        expect(scienceSetting.active).toEqual(activeStatus);
    });

    it('should reuse settings via service if they were already loaded', () => {
        const settingGetMock = vi.spyOn(scienceSettingsServiceMock, 'getScienceSettings').mockReturnValue([scienceSetting]);
        comp.ngOnInit();
        expect(settingGetMock).toHaveBeenCalledOnce();
        // check if current settings are not empty
        expect(comp.userSettings()).toEqual(scienceSettingsStructure);
    });

    // Regression test for issue #13173: the inherited userSettings/settings signals must exist on the instance so the
    // component actually renders. The previous spec never called detectChanges(), so a fully blank render slipped through.
    it('should inherit the userSettings/settings signals from the base and render the settings content (issue #13173)', () => {
        // The inherited fields must be callable signals, not undefined (a subclass field re-declaration would shadow them).
        expect(typeof comp.userSettings).toBe('function');
        expect(typeof comp.settings).toBe('function');

        vi.spyOn(scienceSettingsServiceMock, 'getScienceSettings').mockReturnValue([scienceSetting]);
        comp.ngOnInit();
        fixture.detectChanges();

        // The settings signal is populated (proving the inherited signal works, not undefined).
        expect(comp.userSettings()).toBeTruthy();

        const element: HTMLElement = fixture.nativeElement;
        // The heading AND the unconditional info line below it must render. In the bug the component threw right after
        // the heading, so only the <h2> showed and this info line (a plain sibling) was missing.
        expect(element.querySelector('h2')).toBeTruthy();
        expect(element.querySelector('.userSettings-info')).toBeTruthy();
    });
});
