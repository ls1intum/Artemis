import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisChatSubSettings, IrisCodeEditorSubSettings, IrisHestiaSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisGlobalSettings, IrisSettings, IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { IrisSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/button.component';
import { HttpResponse } from '@angular/common/http';
import { IrisModel } from 'app/entities/iris/settings/iris-model';
import { IrisChatSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-chat-sub-settings-update/iris-chat-sub-settings-update.component';
import { IrisHestiaSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-hestia-sub-settings-update/iris-hestia-sub-settings-update.component';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { IrisGlobalAutoupdateSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-global-autoupdate-settings-update/iris-global-autoupdate-settings-update.component';

function baseSettings() {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = 1;
    mockTemplate.content = 'Hello World';
    const mockChatSettings = new IrisChatSubSettings();
    mockChatSettings.id = 1;
    mockChatSettings.template = mockTemplate;
    mockChatSettings.enabled = true;
    const mockHestiaSettings = new IrisHestiaSubSettings();
    mockHestiaSettings.id = 2;
    mockHestiaSettings.template = mockTemplate;
    mockHestiaSettings.enabled = true;
    const mockCodeEditorSettings = new IrisCodeEditorSubSettings();
    mockCodeEditorSettings.id = 2;
    mockCodeEditorSettings.enabled = false;
    const irisSettings = new IrisGlobalSettings();
    irisSettings.id = 1;
    irisSettings.irisChatSettings = mockChatSettings;
    irisSettings.irisHestiaSettings = mockHestiaSettings;
    irisSettings.irisCodeEditorSettings = mockCodeEditorSettings;
    return irisSettings;
}

function models() {
    return [
        {
            id: '1',
            name: 'Model 1',
            description: 'Model 1 Description',
        },
        {
            id: '2',
            name: 'Model 2',
            description: 'Model 2 Description',
        },
    ] as IrisModel[];
}

describe('IrisSettingsUpdateComponent Component', () => {
    let comp: IrisSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                IrisSettingsUpdateComponent,
                MockComponent(IrisCommonSubSettingsUpdateComponent),
                MockComponent(IrisChatSubSettingsUpdateComponent),
                MockComponent(IrisHestiaSubSettingsUpdateComponent),
                MockComponent(IrisGlobalAutoupdateSettingsUpdateComponent),
                MockComponent(ButtonComponent),
            ],
            providers: [MockProvider(IrisSettingsService)],
        })
            .compileComponents()
            .then(() => {
                irisSettingsService = TestBed.inject(IrisSettingsService);
            });
        fixture = TestBed.createComponent(IrisSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Loads global settings correctly', () => {
        const irisSettings = baseSettings();
        const getSettingsSpy = jest.spyOn(irisSettingsService, 'getGlobalSettings').mockReturnValue(of(irisSettings));
        const getModelsSpy = jest.spyOn(irisSettingsService, 'getIrisModels').mockReturnValue(of(models()));
        comp.settingsType = IrisSettingsType.GLOBAL;
        fixture.detectChanges();
        expect(getSettingsSpy).toHaveBeenCalledOnce();
        expect(getModelsSpy).toHaveBeenCalledOnce();
        expect(comp.irisSettings).toEqual(irisSettings);
        expect(fixture.debugElement.nativeElement.querySelector('#inheritHestia')).toBeFalsy();
    });

    it('Loads course settings correctly', () => {
        const irisSettings = baseSettings();
        const getSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings').mockReturnValue(of(irisSettings));
        const getModelsSpy = jest.spyOn(irisSettingsService, 'getIrisModels').mockReturnValue(of(models()));
        const getParentSettingsSpy = jest.spyOn(irisSettingsService, 'getGlobalSettings').mockReturnValue(of(irisSettings));
        comp.settingsType = IrisSettingsType.COURSE;
        comp.courseId = 1;
        fixture.detectChanges();
        expect(getSettingsSpy).toHaveBeenCalledWith(1);
        expect(getModelsSpy).toHaveBeenCalledOnce();
        expect(getParentSettingsSpy).toHaveBeenCalledOnce();
        expect(comp.irisSettings).toEqual(irisSettings);
    });

    it('Loads programming exercise settings correctly', () => {
        const irisSettings = baseSettings();
        const getSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedProgrammingExerciseSettings').mockReturnValue(of(irisSettings));
        const getModelsSpy = jest.spyOn(irisSettingsService, 'getIrisModels').mockReturnValue(of(models()));
        const getParentSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
        comp.settingsType = IrisSettingsType.EXERCISE;
        comp.exerciseId = 1;
        fixture.detectChanges();
        expect(getSettingsSpy).toHaveBeenCalledWith(1);
        expect(getModelsSpy).toHaveBeenCalledOnce();
        expect(getParentSettingsSpy).toHaveBeenCalledOnce();
        expect(comp.irisSettings).toEqual(irisSettings);
    });

    it('Saves global settings correctly', () => {
        const irisSettings = baseSettings();
        irisSettings.id = undefined;
        const irisSettingsSaved = baseSettings();
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setGlobalSettings').mockReturnValue(of(new HttpResponse<IrisSettings>({ body: irisSettingsSaved })));
        comp.settingsType = IrisSettingsType.GLOBAL;
        comp.irisSettings = irisSettings;
        comp.saveIrisSettings();
        expect(setSettingsSpy).toHaveBeenCalledWith(irisSettings);
        expect(comp.irisSettings).toEqual(irisSettingsSaved);
    });

    it('Saves course settings correctly', () => {
        const irisSettings = baseSettings();
        irisSettings.id = undefined;
        const irisSettingsSaved = baseSettings();
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setCourseSettings').mockReturnValue(of(new HttpResponse<IrisSettings>({ body: irisSettingsSaved })));
        comp.settingsType = IrisSettingsType.COURSE;
        comp.courseId = 1;
        comp.irisSettings = irisSettings;
        comp.saveIrisSettings();
        expect(setSettingsSpy).toHaveBeenCalledWith(1, irisSettings);
        expect(comp.irisSettings).toEqual(irisSettingsSaved);
    });

    it('Saves programming exercise settings correctly', () => {
        const irisSettings = baseSettings();
        irisSettings.id = undefined;
        const irisSettingsSaved = baseSettings();
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setProgrammingExerciseSettings').mockReturnValue(of(new HttpResponse<IrisSettings>({ body: irisSettingsSaved })));
        comp.settingsType = IrisSettingsType.EXERCISE;
        comp.exerciseId = 1;
        comp.irisSettings = irisSettings;
        comp.saveIrisSettings();
        expect(setSettingsSpy).toHaveBeenCalledWith(1, irisSettings);
        expect(comp.irisSettings).toEqual(irisSettingsSaved);
    });
});
