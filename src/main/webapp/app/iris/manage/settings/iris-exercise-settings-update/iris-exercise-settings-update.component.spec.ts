import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { mockSettings } from 'test/helpers/mocks/iris/mock-settings';
import { IrisExerciseSettingsUpdateComponent } from 'app/iris/manage/settings/iris-exercise-settings-update/iris-exercise-settings-update.component';
import { ActivatedRoute, Params, provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { HttpResponse } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

describe('IrisExerciseSettingsUpdateComponent Component', () => {
    let comp: IrisExerciseSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisExerciseSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    let featureToggleService: FeatureToggleService;
    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: 1, exerciseId: 1 });
    const route = { parent: { params: routeParamsSubject.asObservable() } } as ActivatedRoute;
    let paramsSpy: jest.SpyInstance;
    let getSettingsSpy: jest.SpyInstance;
    let getParentSettingsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [IrisExerciseSettingsUpdateComponent, IrisSettingsUpdateComponent, MockComponent(IrisCommonSubSettingsUpdateComponent), MockComponent(ButtonComponent)],
            providers: [
                provideRouter([]),
                MockProvider(IrisSettingsService),
                MockProvider(FeatureToggleService),
                { provide: ActivatedRoute, useValue: route },
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
                routeParamsSubject.next({ courseId: 1, exerciseId: 2 });
                paramsSpy = jest.spyOn(route.parent!.params, 'subscribe');

                const irisSettings = mockSettings();
                getSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedExerciseSettings').mockReturnValue(of(irisSettings));
                getParentSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
            });
        fixture = TestBed.createComponent(IrisExerciseSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Setup works correctly', () => {
        fixture.detectChanges();
        expect(paramsSpy).toHaveBeenCalledOnce();
        expect(comp.courseId).toBe(1);
        expect(comp.exerciseId).toBe(2);
        expect(comp.settingsUpdateComponent).toBeTruthy();
        expect(getSettingsSpy).toHaveBeenCalledWith(2);
        expect(getParentSettingsSpy).toHaveBeenCalledWith(1);

        expect(fixture.debugElement.queryAll(By.directive(IrisCommonSubSettingsUpdateComponent))).toHaveLength(2);
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
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setExerciseSettings').mockReturnValue(of(new HttpResponse<IrisSettings>({ body: irisSettingsSaved })));
        comp.settingsUpdateComponent!.irisSettings = irisSettings;
        comp.settingsUpdateComponent!.saveIrisSettings();
        expect(setSettingsSpy).toHaveBeenCalledWith(2, irisSettings);
        expect(comp.settingsUpdateComponent!.irisSettings).toEqual(irisSettingsSaved);
    });

    it('Updates ids when route params change after init', () => {
        fixture.detectChanges();
        routeParamsSubject.next({ courseId: 5, exerciseId: 42 });
        fixture.detectChanges();
        expect(comp.courseId).toBe(5);
        expect(comp.exerciseId).toBe(42);
    });

    it('Handles missing child settings component gracefully', () => {
        fixture.detectChanges();
        comp.settingsUpdateComponent = undefined;
        expect(comp.canDeactivate()).toBeTrue();
        expect(comp.canDeactivateWarning).toBeUndefined();
    });

    it('Does not render settings update when ids are missing', () => {
        routeParamsSubject.next({});
        fixture.detectChanges();

        expect(comp.courseId).toBeNaN();
        expect(comp.exerciseId).toBeNaN();
        expect(fixture.debugElement.query(By.directive(IrisSettingsUpdateComponent))).toBeNull();
        expect(getSettingsSpy).not.toHaveBeenCalled();
        expect(getParentSettingsSpy).not.toHaveBeenCalled();
    });

    it('Parses numeric ids from string route params', () => {
        routeParamsSubject.next({ courseId: '11', exerciseId: '13' });
        fixture.detectChanges();

        expect(comp.courseId).toBe(11);
        expect(comp.exerciseId).toBe(13);
        expect(getSettingsSpy).toHaveBeenCalledWith(13);
        expect(getParentSettingsSpy).toHaveBeenCalledWith(11);
    });

    it('should not overwrite settings when save fails', fakeAsync(() => {
        fixture.detectChanges();
        const irisSettings = mockSettings();
        irisSettings.id = undefined;
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setExerciseSettings').mockReturnValue(throwError(() => new Error('save-failed')));
        comp.settingsUpdateComponent!.irisSettings = { ...irisSettings };
        const before = { ...comp.settingsUpdateComponent!.irisSettings! };

        comp.settingsUpdateComponent!.saveIrisSettings();
        tick();

        expect(setSettingsSpy).toHaveBeenCalledWith(2, before);
        expect(comp.settingsUpdateComponent!.irisSettings).toEqual(before);
    }));
});
