import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { IrisEventType, IrisProgrammingExerciseChatSubSettings } from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { mockVariants } from 'test/helpers/mocks/iris/mock-settings';
import { IrisSettingsType } from 'app/iris/shared/entities/settings/iris-settings.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { of, throwError } from 'rxjs';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import * as globalUtils from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

function baseSettings() {
    const irisSubSettings = new IrisProgrammingExerciseChatSubSettings();
    irisSubSettings.id = 2;
    irisSubSettings.enabled = true;
    const allowedVariants = mockVariants();
    allowedVariants.pop();
    irisSubSettings.allowedVariants = allowedVariants.map((model) => model.id!);
    irisSubSettings.selectedVariant = allowedVariants[0].id!;
    return irisSubSettings;
}

function mockCategories() {
    return [
        // Convert ExerciseCategory to json string
        JSON.stringify(new ExerciseCategory('category1', '0xff0000')),
        JSON.stringify(new ExerciseCategory('category2', '0x00ff00')),
        JSON.stringify(new ExerciseCategory('category3', '0x0000ff')),
    ];
}

describe('IrisCommonSubSettingsUpdateComponent Component', () => {
    let comp: IrisCommonSubSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisCommonSubSettingsUpdateComponent>;
    let getVariantsSpy: jest.SpyInstance;
    let getCategoriesSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe), MockJhiTranslateDirective],
            declarations: [IrisCommonSubSettingsUpdateComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                const irisSettingsService = TestBed.inject(IrisSettingsService);
                const courseManagementService = TestBed.inject(CourseManagementService);
                getVariantsSpy = jest.spyOn(irisSettingsService, 'getVariantsForFeature').mockReturnValue(of(mockVariants()));
                getCategoriesSpy = jest.spyOn(courseManagementService, 'findAllCategoriesOfCourse').mockReturnValue(of(new HttpResponse({ body: mockCategories() })));
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

    it('should not call getVariants if subSettings type is missing', () => {
        const subSettings = baseSettings();
        (subSettings as any).type = undefined;
        comp.subSettings = subSettings;
        fixture.detectChanges();
        expect(getVariantsSpy).not.toHaveBeenCalled();
    });

    it('should handle error when loading categories', fakeAsync(() => {
        const error = new HttpErrorResponse({ status: 500 });
        const onErrorSpy = jest.spyOn(globalUtils, 'onError').mockImplementation();
        getCategoriesSpy.mockReturnValue(throwError(() => error));

        comp.settingsType = IrisSettingsType.COURSE;
        comp.courseId = 1;
        comp.loadCategories();
        tick();

        expect(getCategoriesSpy).toHaveBeenCalledOnce();
        expect(onErrorSpy).toHaveBeenCalledOnce();
    }));

    it('should get selected variant name', () => {
        comp.subSettings = baseSettings();
        comp.availableVariants = mockVariants();
        fixture.detectChanges();

        expect(comp.getSelectedVariantName()).toBe(mockVariants()[0].name);

        comp.subSettings.selectedVariant = 'non-existent';
        expect(comp.getSelectedVariantName()).toBe('non-existent');
    });

    it('should get selected variant name from parent', () => {
        comp.parentSubSettings = baseSettings();
        comp.availableVariants = mockVariants();
        fixture.detectChanges();

        expect(comp.getSelectedVariantNameParent()).toBe(mockVariants()[0].name);

        comp.parentSubSettings.selectedVariant = 'non-existent';
        expect(comp.getSelectedVariantNameParent()).toBe('non-existent');
    });

    it('should toggle proactive events', () => {
        comp.subSettings = baseSettings();
        comp.subSettings.disabledProactiveEvents = [];
        fixture.detectChanges();

        comp.onEventToggleChange(IrisEventType.BUILD_FAILED);
        expect(comp.subSettings.disabledProactiveEvents).toEqual([IrisEventType.BUILD_FAILED]);

        comp.onEventToggleChange(IrisEventType.BUILD_FAILED);
        expect(comp.subSettings.disabledProactiveEvents).toEqual([]);

        comp.subSettings = undefined;
        comp.onEventToggleChange(IrisEventType.BUILD_FAILED);
        expect(comp.subSettings).toBeUndefined();
    });

    it('should update custom instructions', () => {
        comp.subSettings = baseSettings();
        fixture.detectChanges();

        const instructions = 'New custom instructions';
        comp.onCustomInstructionsChange(instructions);
        expect(comp.subSettings.customInstructions).toBe(instructions);

        comp.subSettings = undefined;
        comp.onCustomInstructionsChange(instructions);
        expect(comp.subSettings).toBeUndefined();
    });

    it('ngOnChanges should update event disabled status', () => {
        const subSettings = baseSettings();
        const parentSubSettings = baseSettings();
        parentSubSettings.enabled = false;
        comp.subSettings = subSettings;
        comp.parentSubSettings = parentSubSettings;
        comp.exerciseChatEvents = [IrisEventType.BUILD_FAILED];
        const updateEventDisabledStatusSpy = jest.spyOn(comp as any, 'updateEventDisabledStatus');

        const changes: SimpleChanges = {
            parentSubSettings: new SimpleChange(undefined, parentSubSettings, true),
        };
        comp.ngOnChanges(changes);

        expect(updateEventDisabledStatusSpy).toHaveBeenCalledOnce();
        expect(comp.eventInParentDisabledStatusMap.get(IrisEventType.BUILD_FAILED)).toBeTrue();

        parentSubSettings.enabled = true;
        const changes2: SimpleChanges = {
            parentSubSettings: new SimpleChange(parentSubSettings, parentSubSettings, false),
        };
        comp.ngOnChanges(changes2);
        expect(comp.eventInParentDisabledStatusMap.get(IrisEventType.BUILD_FAILED)).toBeFalse();
    });

    it('enable categories', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        comp.parentSubSettings.enabled = false;
        comp.isAdmin = true;
        comp.settingsType = IrisSettingsType.COURSE;
        comp.availableVariants = mockVariants();
        fixture.detectChanges();

        expect(getCategoriesSpy).toHaveBeenCalledOnce();

        comp.onCategorySelectionChange('category1');
        expect(comp.subSettings!.enabledForCategories).toEqual(['category1']);
        comp.onCategorySelectionChange('category2');
        expect(comp.subSettings!.enabledForCategories).toEqual(['category1', 'category2']);
        comp.onCategorySelectionChange('category1');
        expect(comp.subSettings!.enabledForCategories).toEqual(['category2']);

        comp.subSettings = undefined;
        comp.onCategorySelectionChange('category1');
        expect(comp.subSettings).toBeUndefined();
    });
});
