import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisChatSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective, MockPipe } from 'ng-mocks';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { mockModels } from './mock-settings';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

function baseSettings() {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = 1;
    mockTemplate.content = 'Hello World';
    const irisSubSettings = new IrisChatSubSettings();
    irisSubSettings.id = 2;
    irisSubSettings.enabled = true;
    const allowedModels = mockModels();
    allowedModels.pop();
    irisSubSettings.allowedModels = allowedModels.map((model) => model.id!);
    irisSubSettings.preferredModel = allowedModels[0].id!;
    return irisSubSettings;
}

describe('IrisCommonSubSettingsUpdateComponent Component', () => {
    let comp: IrisCommonSubSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisCommonSubSettingsUpdateComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe)],
            declarations: [IrisCommonSubSettingsUpdateComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(IrisCommonSubSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('child setup works', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.allIrisModels = mockModels();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        expect(comp.enabled).toBeTrue();
        expect(comp.inheritAllowedModels).toBeFalse();
        expect(comp.allowedIrisModels).toEqual([mockModels()[0]]);
    });

    it('parent setup works', () => {
        const subSettings = baseSettings();
        subSettings.allowedModels = undefined;
        subSettings.preferredModel = undefined;
        comp.subSettings = subSettings;
        comp.parentSubSettings = baseSettings();
        comp.allIrisModels = mockModels();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        expect(comp.enabled).toBeTrue();
        expect(comp.inheritAllowedModels).toBeTrue();
        expect(comp.allowedIrisModels).toEqual([mockModels()[0]]);
    });

    it('prevents enabling chat settings if the parent chat settings disabled', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.parentSubSettings.enabled = false;
        comp.isAdmin = true;
        comp.settingsType = IrisSettingsType.EXERCISE;
        comp.allIrisModels = mockModels();
        fixture.detectChanges();

        expect(comp.inheritDisabled).toBeTrue();
        expect(comp.isSettingsSwitchDisabled).toBeTrue();
    });

    it('change allowed model', () => {
        const allIrisModels = mockModels();
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.allIrisModels = allIrisModels;
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        comp.onAllowedIrisModelsSelectionChange(allIrisModels[1]);
        expect(comp.allowedIrisModels).toEqual([allIrisModels[0], allIrisModels[1]]);
        comp.onAllowedIrisModelsSelectionChange(allIrisModels[0]);
        expect(comp.allowedIrisModels).toEqual([allIrisModels[1]]);
    });

    it('change preferred model', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.allIrisModels = mockModels();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        comp.setModel(mockModels()[1]);
        expect(comp.subSettings!.preferredModel).toBe(mockModels()[1].id);
    });

    it('change enabled', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.allIrisModels = mockModels();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        comp.onDisable();
        expect(comp.enabled).toBeFalse();
        expect(comp.subSettings!.enabled).toBeFalse();

        comp.onEnable();
        expect(comp.enabled).toBeTrue();
        expect(comp.subSettings!.enabled).toBeTrue();
    });

    it('change inherit allowed models', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.allIrisModels = mockModels();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        comp.inheritAllowedModels = true;
        comp.onInheritAllowedModelsChange();
        expect(comp.subSettings!.allowedModels).toBeUndefined();
        expect(comp.allowedIrisModels).toEqual(comp.getAvailableModels());

        comp.inheritAllowedModels = false;
        comp.onInheritAllowedModelsChange();
        expect(comp.subSettings!.allowedModels).toEqual(comp.allowedIrisModels.map((model) => model.id));
    });

    it('ngOnChanges works', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.allIrisModels = mockModels();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        const newSubSettings = baseSettings();
        newSubSettings.enabled = false;
        const newModels = mockModels();
        newModels.pop();

        const changes: SimpleChanges = {
            subSettings: new SimpleChange(comp.subSettings, newSubSettings, false),
            allIrisModels: new SimpleChange(comp.allIrisModels, newModels, false),
        };
        comp.subSettings = newSubSettings;
        comp.allIrisModels = mockModels();
        comp.ngOnChanges(changes);

        expect(comp.enabled).toBeFalse();
        expect(comp.allowedIrisModels).toEqual(newModels);
    });
});
