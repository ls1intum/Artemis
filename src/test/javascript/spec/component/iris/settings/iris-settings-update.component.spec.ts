import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { IrisSettingsType, IrisSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { IrisSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-sub-settings-update/iris-sub-settings-update.component';
import { ButtonComponent } from 'app/shared/components/button.component';

function baseSettings() {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = 1;
    mockTemplate.content = 'Hello World';
    const mockChatSettings = new IrisSubSettings();
    mockChatSettings.id = 1;
    mockChatSettings.template = mockTemplate;
    mockChatSettings.enabled = true;
    const mockHestiaSettings = new IrisSubSettings();
    mockHestiaSettings.id = 2;
    mockHestiaSettings.template = mockTemplate;
    mockHestiaSettings.enabled = true;
    const irisSettings = new IrisSettings();
    irisSettings.id = 1;
    irisSettings.irisChatSettings = mockChatSettings;
    irisSettings.irisHestiaSettings = mockHestiaSettings;
    return irisSettings;
}

describe('IrisSettingsUpdateComponent Component', () => {
    let comp: IrisSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [IrisSettingsUpdateComponent, MockComponent(IrisSubSettingsUpdateComponent), MockComponent(ButtonComponent)],
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
        comp.settingType = IrisSettingsType.GLOBAL;
        fixture.detectChanges();
        expect(getSettingsSpy).toHaveBeenCalledOnce();
        expect(comp.irisSettings).toEqual(irisSettings);
        expect(fixture.debugElement.nativeElement.querySelector('#inheritHestia')).toBeFalsy();
    });

    it('Loads course settings correctly', () => {
        const irisSettings = baseSettings();
        const getSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings').mockReturnValue(of(irisSettings));
        comp.settingType = IrisSettingsType.COURSE;
        comp.courseId = 1;
        fixture.detectChanges();
        expect(getSettingsSpy).toHaveBeenCalledWith(1);
        expect(comp.irisSettings).toEqual(irisSettings);
        expect(fixture.debugElement.nativeElement.querySelector('#inheritHestia')).toBeFalsy();
    });

    it('Loads programming exercise settings correctly', () => {
        const irisSettings = baseSettings();
        const getSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedProgrammingExerciseSettings').mockReturnValue(of(irisSettings));
        comp.settingType = IrisSettingsType.PROGRAMMING_EXERCISE;
        comp.programmingExerciseId = 1;
        fixture.detectChanges();
        expect(getSettingsSpy).toHaveBeenCalledWith(1);
        expect(comp.irisSettings).toEqual(irisSettings);
        expect(fixture.debugElement.nativeElement.querySelector('#inheritHestia')).toBeTruthy();
    });
});
