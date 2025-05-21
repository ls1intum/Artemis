import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { mockSettings } from 'test/helpers/mocks/iris/mock-settings';
import { IrisGlobalSettingsUpdateComponent } from 'app/iris/manage/settings/iris-global-settings-update/iris-global-settings-update.component';
import { By } from '@angular/platform-browser';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { HttpResponse } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

describe('IrisGlobalSettingsUpdateComponent Component', () => {
    let comp: IrisGlobalSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisGlobalSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    let featureToggleService: FeatureToggleService;
    let getSettingsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [IrisGlobalSettingsUpdateComponent, IrisSettingsUpdateComponent, MockComponent(IrisCommonSubSettingsUpdateComponent), MockComponent(ButtonComponent)],
            providers: [
                MockProvider(IrisSettingsService),
                MockProvider(FeatureToggleService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                irisSettingsService = TestBed.inject(IrisSettingsService);
                featureToggleService = TestBed.inject(FeatureToggleService);
                jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));

                // Setup
                const irisSettings = mockSettings();
                getSettingsSpy = jest.spyOn(irisSettingsService, 'getGlobalSettings').mockReturnValue(of(irisSettings));
            });
        fixture = TestBed.createComponent(IrisGlobalSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create IrisGlobalSettingsUpdateComponent', () => {
        expect(comp).toBeDefined();
    });

    it('Setup works correctly', () => {
        fixture.detectChanges();
        expect(comp.settingsUpdateComponent).toBeTruthy();
        expect(getSettingsSpy).toHaveBeenCalledOnce();

        expect(fixture.debugElement.queryAll(By.directive(IrisCommonSubSettingsUpdateComponent))).toHaveLength(8);
    });

    it('Can deactivate correctly', () => {
        fixture.detectChanges();
        expect(comp.canDeactivate()).toBeTrue();
        comp.settingsUpdateComponent!.isDirty = true;
        expect(comp.canDeactivate()).toBeFalse();
        comp.settingsUpdateComponent!.canDeactivateWarning = 'Warning';
        expect(comp.canDeactivateWarning).toBe('Warning');
    });

    it('Saves settings correctly', () => {
        fixture.detectChanges();
        const irisSettings = mockSettings();
        irisSettings.id = undefined;
        const irisSettingsSaved = mockSettings();
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setGlobalSettings').mockReturnValue(of(new HttpResponse<IrisSettings>({ body: irisSettingsSaved })));
        comp.settingsUpdateComponent!.irisSettings = irisSettings;
        comp.settingsUpdateComponent!.saveIrisSettings();
        expect(setSettingsSpy).toHaveBeenCalledWith(irisSettings);
        expect(comp.settingsUpdateComponent!.irisSettings).toEqual(irisSettingsSaved);
    });
});
