import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/button.component';
import { IrisChatSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-chat-sub-settings-update/iris-chat-sub-settings-update.component';
import { IrisHestiaSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-hestia-sub-settings-update/iris-hestia-sub-settings-update.component';
import { IrisCodeEditorSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-code-editor-sub-settings-update/iris-code-editor-sub-settings-update.component';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { IrisGlobalAutoupdateSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-global-autoupdate-settings-update/iris-global-autoupdate-settings-update.component';
import { mockModels, mockSettings } from './mock-settings';
import { NgModel } from '@angular/forms';
import { IrisGlobalSettingsUpdateComponent } from 'app/iris/settings/iris-global-settings-update/iris-global-settings-update.component';
import { By } from '@angular/platform-browser';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { HttpResponse } from '@angular/common/http';
import { IrisCompetencyGenerationSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-competency-generation-sub-settings-update/iris-competency-generation-sub-settings-update.component';

describe('IrisGlobalSettingsUpdateComponent Component', () => {
    let comp: IrisGlobalSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisGlobalSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    let getSettingsSpy: jest.SpyInstance;
    let getModelsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                IrisGlobalSettingsUpdateComponent,
                IrisSettingsUpdateComponent,
                MockComponent(IrisCommonSubSettingsUpdateComponent),
                MockComponent(IrisChatSubSettingsUpdateComponent),
                MockComponent(IrisHestiaSubSettingsUpdateComponent),
                MockComponent(IrisCodeEditorSubSettingsUpdateComponent),
                MockComponent(IrisCompetencyGenerationSubSettingsUpdateComponent),
                MockComponent(IrisGlobalAutoupdateSettingsUpdateComponent),
                MockComponent(ButtonComponent),
                MockDirective(NgModel),
            ],
            providers: [MockProvider(IrisSettingsService)],
        })
            .compileComponents()
            .then(() => {
                irisSettingsService = TestBed.inject(IrisSettingsService);

                // Setup
                const irisSettings = mockSettings();
                getSettingsSpy = jest.spyOn(irisSettingsService, 'getGlobalSettings').mockReturnValue(of(irisSettings));
                getModelsSpy = jest.spyOn(irisSettingsService, 'getIrisModels').mockReturnValue(of(mockModels()));
            });
        fixture = TestBed.createComponent(IrisGlobalSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Setup works correctly', () => {
        fixture.detectChanges();
        expect(comp.settingsUpdateComponent).toBeTruthy();
        expect(getSettingsSpy).toHaveBeenCalledOnce();
        expect(getModelsSpy).toHaveBeenCalledOnce();

        expect(fixture.debugElement.query(By.directive(IrisGlobalAutoupdateSettingsUpdateComponent))).toBeTruthy();
        expect(fixture.debugElement.queryAll(By.directive(IrisCommonSubSettingsUpdateComponent))).toHaveLength(4);
        expect(fixture.debugElement.query(By.directive(IrisChatSubSettingsUpdateComponent))).toBeTruthy();
        expect(fixture.debugElement.query(By.directive(IrisHestiaSubSettingsUpdateComponent))).toBeTruthy();
        expect(fixture.debugElement.query(By.directive(IrisCodeEditorSubSettingsUpdateComponent))).toBeTruthy();
        expect(fixture.debugElement.query(By.directive(IrisCompetencyGenerationSubSettingsUpdateComponent))).toBeTruthy();
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
