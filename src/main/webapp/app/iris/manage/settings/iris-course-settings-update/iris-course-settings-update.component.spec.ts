import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { mockSettings } from 'test/helpers/mocks/iris/mock-settings';
import { ActivatedRoute, Params, provideRouter } from '@angular/router';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/manage/settings/iris-course-settings-update/iris-course-settings-update.component';
import { By } from '@angular/platform-browser';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { HttpResponse } from '@angular/common/http';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

describe('IrisCourseSettingsUpdateComponent Component', () => {
    let comp: IrisCourseSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisCourseSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    let featureToggleService: FeatureToggleService;
    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: '1' });
    const route = { params: routeParamsSubject.asObservable() } as ActivatedRoute;
    let paramsSpy: jest.SpyInstance;
    let getSettingsSpy: jest.SpyInstance;
    let getParentSettingsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockJhiTranslateDirective],
            declarations: [IrisCourseSettingsUpdateComponent, IrisSettingsUpdateComponent, MockComponent(IrisCommonSubSettingsUpdateComponent), MockComponent(ButtonComponent)],
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
                routeParamsSubject.next({ courseId: 1 });
                paramsSpy = jest.spyOn(route!.params, 'subscribe');

                const irisSettings = mockSettings();
                getSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings').mockReturnValue(of(irisSettings));
                getParentSettingsSpy = jest.spyOn(irisSettingsService, 'getGlobalSettings').mockReturnValue(of(irisSettings));
            });
        fixture = TestBed.createComponent(IrisCourseSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create IrisCourseSettingsUpdateComponent', () => {
        expect(comp).toBeDefined();
    });

    it('Setup works correctly', () => {
        fixture.detectChanges();
        expect(paramsSpy).toHaveBeenCalledOnce();
        expect(comp.courseId).toBe(1);
        expect(comp.settingsUpdateComponent).toBeTruthy();
        expect(getSettingsSpy).toHaveBeenCalledWith(1);
        expect(getParentSettingsSpy).toHaveBeenCalledOnce();

        expect(fixture.debugElement.queryAll(By.directive(IrisCommonSubSettingsUpdateComponent))).toHaveLength(8);
    });

    it('should not render settings update component if courseId is not present', () => {
        routeParamsSubject.next({}); // Simulate route without courseId
        fixture.detectChanges();

        expect(paramsSpy).toHaveBeenCalledOnce();
        expect(comp.courseId).toBeNaN();
        expect(fixture.debugElement.query(By.directive(IrisSettingsUpdateComponent))).toBeNull();
    });

    it('Can deactivate correctly', () => {
        fixture.detectChanges();
        expect(comp.canDeactivate()).toBeTrue();
        comp.settingsUpdateComponent!.isDirty = true;
        expect(comp.canDeactivate()).toBeFalse();
        comp.settingsUpdateComponent!.canDeactivateWarning = 'Warning';
        expect(comp.canDeactivateWarning).toBe('Warning');
    });

    it('should allow deactivation if settings component is not defined', () => {
        comp.settingsUpdateComponent = undefined;
        expect(comp.canDeactivate()).toBeTrue();
        expect(comp.canDeactivateWarning).toBeUndefined();
    });

    it('Saves settings correctly', () => {
        fixture.detectChanges();
        const irisSettings = mockSettings();
        irisSettings.id = undefined;
        const irisSettingsSaved = mockSettings();
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setCourseSettings').mockReturnValue(of(new HttpResponse<IrisSettings>({ body: irisSettingsSaved })));
        comp.settingsUpdateComponent!.irisSettings = irisSettings;
        comp.settingsUpdateComponent!.saveIrisSettings();
        expect(setSettingsSpy).toHaveBeenCalledWith(1, irisSettings);
        expect(comp.settingsUpdateComponent!.irisSettings).toEqual(irisSettingsSaved);
    });
});
