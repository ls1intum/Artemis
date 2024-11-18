import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/button.component';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { mockSettings } from './mock-settings';
import { NgModel } from '@angular/forms';
import { IrisGlobalSettingsUpdateComponent } from 'app/iris/settings/iris-global-settings-update/iris-global-settings-update.component';
import { By } from '@angular/platform-browser';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { HttpResponse } from '@angular/common/http';
import { IrisEventSettingsUpdateComponent } from '../../../../../../main/webapp/app/iris/settings/iris-settings-update/iris-event-settings-update/iris-event-settings-update.component';

describe('IrisGlobalSettingsUpdateComponent Component', () => {
    let comp: IrisGlobalSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisGlobalSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    let getSettingsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                IrisGlobalSettingsUpdateComponent,
                IrisSettingsUpdateComponent,
                MockComponent(IrisEventSettingsUpdateComponent),
                MockComponent(IrisCommonSubSettingsUpdateComponent),
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
            });
        TestBed.createComponent(IrisEventSettingsUpdateComponent);
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

        expect(fixture.debugElement.queryAll(By.directive(IrisCommonSubSettingsUpdateComponent))).toHaveLength(5);
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
        irisSettings.irisProactivitySettings!.eventSettings.forEach((eventSetting) => (eventSetting.id = undefined));
        const irisSettingsSaved = mockSettings();
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setGlobalSettings').mockReturnValue(of(new HttpResponse<IrisSettings>({ body: irisSettingsSaved })));
        comp.settingsUpdateComponent!.irisSettings = irisSettings;
        comp.settingsUpdateComponent!.saveIrisSettings();
        expect(setSettingsSpy).toHaveBeenCalledWith(irisSettings);
        expect(comp.settingsUpdateComponent!.irisSettings).toEqual(irisSettingsSaved);
    });
});
