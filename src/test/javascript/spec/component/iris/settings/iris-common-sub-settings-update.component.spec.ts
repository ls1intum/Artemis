import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { IrisChatSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective, MockPipe } from 'ng-mocks';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { mockVariants } from './mock-settings';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { of } from 'rxjs';

function baseSettings() {
    const irisSubSettings = new IrisChatSubSettings();
    irisSubSettings.id = 2;
    irisSubSettings.enabled = true;
    const allowedVariants = mockVariants();
    allowedVariants.pop();
    irisSubSettings.allowedVariants = allowedVariants.map((model) => model.id!);
    irisSubSettings.selectedVariant = allowedVariants[0].id!;
    return irisSubSettings;
}

describe('IrisCommonSubSettingsUpdateComponent Component', () => {
    let comp: IrisCommonSubSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisCommonSubSettingsUpdateComponent>;
    let getVariantsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe)],
            declarations: [IrisCommonSubSettingsUpdateComponent],
        })
            .compileComponents()
            .then(() => {
                const irisSettingsService = TestBed.inject(IrisSettingsService);
                getVariantsSpy = jest.spyOn(irisSettingsService, 'getVariantsForFeature').mockReturnValue(of(mockVariants()));
            });
        fixture = TestBed.createComponent(IrisCommonSubSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('child setup works', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.availableVariants = mockVariants();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();
        expect(getVariantsSpy).toHaveBeenCalledOnce();

        expect(comp.enabled).toBeTrue();
        expect(comp.inheritAllowedVariants).toBeFalse();
        expect(comp.allowedVariants).toEqual([mockVariants()[0]]);
    });

    it('parent setup works', () => {
        const subSettings = baseSettings();
        subSettings.allowedVariants = undefined;
        subSettings.selectedVariant = undefined;
        comp.subSettings = subSettings;
        comp.parentSubSettings = baseSettings();
        comp.availableVariants = mockVariants();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        expect(comp.enabled).toBeTrue();
        expect(comp.inheritAllowedVariants).toBeTrue();
        expect(comp.allowedVariants).toEqual([mockVariants()[0]]);
    });

    it('prevents enabling chat settings if the parent chat settings disabled', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.parentSubSettings.enabled = false;
        comp.isAdmin = true;
        comp.settingsType = IrisSettingsType.EXERCISE;
        comp.availableVariants = mockVariants();
        fixture.detectChanges();

        expect(comp.inheritDisabled).toBeTrue();
        expect(comp.isSettingsSwitchDisabled).toBeTrue();
    });

    it('prevents enabling settings if the parent chat settings disabled', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.parentSubSettings.enabled = false;
        comp.isAdmin = true;
        comp.settingsType = IrisSettingsType.COURSE;
        comp.availableVariants = mockVariants();
        fixture.detectChanges();

        expect(comp.inheritDisabled).toBeTrue();
        expect(comp.isSettingsSwitchDisabled).toBeTrue();
    });

    it('change allowed model', () => {
        const availableVariants = mockVariants();
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.availableVariants = availableVariants;
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        comp.onAllowedIrisVariantsSelectionChange(availableVariants[1]);
        expect(comp.allowedVariants).toEqual([availableVariants[0], availableVariants[1]]);
        comp.onAllowedIrisVariantsSelectionChange(availableVariants[0]);
        expect(comp.allowedVariants).toEqual([availableVariants[1]]);
    });

    it('change preferred model', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.availableVariants = mockVariants();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        comp.setVariant(mockVariants()[1]);
        expect(comp.subSettings!.selectedVariant).toBe(mockVariants()[1].id);
    });

    it('change enabled', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.availableVariants = mockVariants();
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
        comp.availableVariants = mockVariants();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        comp.inheritAllowedVariants = true;
        comp.onInheritAllowedVariantsChange();
        expect(comp.subSettings!.allowedVariants).toBeUndefined();
        expect(comp.allowedVariants).toEqual(comp.getAllowedVariants());

        comp.inheritAllowedVariants = false;
        comp.onInheritAllowedVariantsChange();
        expect(comp.subSettings!.allowedVariants).toEqual(comp.allowedVariants.map((model) => model.id));
    });

    it('ngOnChanges works', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.availableVariants = mockVariants();
        comp.settingsType = IrisSettingsType.EXERCISE;
        fixture.detectChanges();

        const newSubSettings = baseSettings();
        newSubSettings.enabled = false;
        const newModels = mockVariants();
        newModels.pop();

        const changes: SimpleChanges = {
            subSettings: new SimpleChange(comp.subSettings, newSubSettings, false),
            availableVariants: new SimpleChange(comp.availableVariants, newModels, false),
        };
        comp.subSettings = newSubSettings;
        comp.availableVariants = mockVariants();
        comp.ngOnChanges(changes);

        expect(comp.enabled).toBeFalse();
        expect(comp.allowedVariants).toEqual(newModels);
    });
});
