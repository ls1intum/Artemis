import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { mockVariants } from 'test/helpers/mocks/iris/mock-settings';
import { ActivatedRoute, Params, provideRouter } from '@angular/router';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/manage/settings/iris-course-settings-update/iris-course-settings-update.component';
import { HttpResponse } from '@angular/common/http';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseIrisSettingsDTO, IrisCourseSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('IrisCourseSettingsUpdateComponent Component', () => {
    let comp: IrisCourseSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisCourseSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    let featureToggleService: FeatureToggleService;
    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: '1' });
    const route = { params: routeParamsSubject.asObservable() } as ActivatedRoute;
    let paramsSpy: jest.SpyInstance;
    let getSettingsSpy: jest.SpyInstance;
    let getVariantsSpy: jest.SpyInstance;

    const mockSettings: IrisCourseSettingsDTO = {
        enabled: true,
        customInstructions: 'Test instructions',
        variant: 'default',
        rateLimit: { requests: 100, timeframeHours: 24 },
    };

    const mockResponse: CourseIrisSettingsDTO = {
        courseId: 1,
        settings: mockSettings,
        effectiveRateLimit: { requests: 100, timeframeHours: 24 },
        applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockJhiTranslateDirective],
            declarations: [IrisCourseSettingsUpdateComponent, IrisSettingsUpdateComponent, MockComponent(ButtonComponent), FaIconComponent],
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

                getSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettings').mockReturnValue(of(mockResponse));
                getVariantsSpy = jest.spyOn(irisSettingsService, 'getVariants').mockReturnValue(of(mockVariants()));
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
        expect(getVariantsSpy).toHaveBeenCalledOnce();
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
        const updatedSettings = { ...mockSettings, enabled: false };
        const updatedResponse: CourseIrisSettingsDTO = { ...mockResponse, settings: updatedSettings };
        const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse<CourseIrisSettingsDTO>({ body: updatedResponse })));

        comp.settingsUpdateComponent!.settings = updatedSettings;
        comp.settingsUpdateComponent!.saveSettings();

        expect(updateSpy).toHaveBeenCalledWith(1, updatedSettings);
        expect(comp.settingsUpdateComponent!.settings).toEqual(updatedSettings);
    });
});
