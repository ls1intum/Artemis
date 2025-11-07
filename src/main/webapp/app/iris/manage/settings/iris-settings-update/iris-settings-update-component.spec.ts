import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { IrisCourseSettings, IrisExerciseSettings, IrisGlobalSettings, IrisSettings, IrisSettingsType } from 'app/iris/shared/entities/settings/iris-settings.model';
import { mockSettings, mockVariants } from 'test/helpers/mocks/iris/mock-settings';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { firstValueFrom, of, throwError } from 'rxjs';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/manage/settings/iris-course-settings-update/iris-course-settings-update.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

describe('IrisSettingsUpdateComponent', () => {
    let component: IrisSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisSettingsUpdateComponent>;
    let getVariantsSpy: jest.SpyInstance;
    let getGlobalSettingsSpy: jest.SpyInstance;
    let getCombinedCourseSettingsSpy: jest.SpyInstance;
    let getUncombinedCourseSettingsSpy: jest.SpyInstance;
    let getUncombinedExerciseSettingsSpy: jest.SpyInstance;
    let setGlobalSettingsSpy: jest.SpyInstance;
    let setCourseSettingsSpy: jest.SpyInstance;
    let setExerciseSettingsSpy: jest.SpyInstance;

    beforeEach(() => {
        const irisSettingsServiceMock = {
            getGlobalSettings: jest.fn().mockReturnValue(of({} as IrisGlobalSettings)),
            getCombinedCourseSettings: jest.fn().mockReturnValue(of({} as IrisCourseSettings)),
            getUncombinedCourseSettings: jest.fn().mockReturnValue(of({} as IrisCourseSettings)),
            getUncombinedExerciseSettings: jest.fn().mockReturnValue(of({} as IrisExerciseSettings)),
            setGlobalSettings: jest.fn().mockReturnValue(of(new HttpResponse({ body: {} as IrisGlobalSettings }))),
            setCourseSettings: jest.fn().mockReturnValue(of(new HttpResponse({ body: {} as IrisCourseSettings }))),
            setExerciseSettings: jest.fn().mockReturnValue(of(new HttpResponse({ body: {} as IrisExerciseSettings }))),
        };

        const featureToggleServiceMock = {
            getFeatureToggleActive: jest.fn().mockReturnValue(of(true)),
        };

        TestBed.configureTestingModule({
            imports: [MockJhiTranslateDirective, IrisCourseSettingsUpdateComponent, IrisSettingsUpdateComponent, IrisCommonSubSettingsUpdateComponent, FaIconComponent],
            declarations: [MockPipe(ArtemisTranslatePipe), MockComponent(ButtonComponent)],
            providers: [
                MockProvider(IrisSettingsService, irisSettingsServiceMock),
                MockProvider(FeatureToggleService, featureToggleServiceMock),
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(IrisSettingsUpdateComponent);
                component = fixture.componentInstance;
                const irisSettingsService = TestBed.inject(IrisSettingsService);
                getVariantsSpy = jest.spyOn(irisSettingsService, 'getVariantsForFeature').mockReturnValue(of(mockVariants()));

                getGlobalSettingsSpy = jest.spyOn(irisSettingsService, 'getGlobalSettings');
                getCombinedCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings');
                getUncombinedCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings');
                getUncombinedExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedExerciseSettings');

                setGlobalSettingsSpy = jest.spyOn(irisSettingsService, 'setGlobalSettings');
                setCourseSettingsSpy = jest.spyOn(irisSettingsService, 'setCourseSettings');
                setExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'setExerciseSettings');
            });
    });

    it('should display the checkbox for lecture ingestion when settingsType is COURSE', fakeAsync(() => {
        component.irisSettings = mockSettings();
        component.settingsType = IrisSettingsType.COURSE;
        fixture.detectChanges();
        tick();
        fixture.detectChanges();
        const lectureIngestionElement = fixture.debugElement.query(By.css('jhi-iris-common-sub-settings-update'));
        const checkboxElement = fixture.debugElement.query(By.css('input[type="checkbox"]#autoLectureIngestion'));
        const labelElement = fixture.debugElement.query(By.css('label[for="autoLectureIngestion"]'));
        const globalSettingsElement = fixture.debugElement.query(By.css('jhi-iris-global-autoupdate-settings-update'));
        expect(globalSettingsElement).toBeFalsy();
        expect(lectureIngestionElement).not.toBeNull();
        expect(checkboxElement).toBeTruthy();
        expect(labelElement).toBeTruthy();
        expect(getVariantsSpy).toHaveBeenCalled();
    }));

    describe('loadParentIrisSettingsObservable', () => {
        it('should call getGlobalSettings for COURSE', async () => {
            component.settingsType = IrisSettingsType.COURSE;

            const result = await firstValueFrom(component.loadParentIrisSettingsObservable());

            expect(getGlobalSettingsSpy).toHaveBeenCalledOnce();
            expect(result).toEqual({});
        });

        it('should call getCombinedCourseSettings for EXERCISE', async () => {
            component.settingsType = IrisSettingsType.EXERCISE;
            component.courseId = 10;

            const result = await firstValueFrom(component.loadParentIrisSettingsObservable());

            expect(getCombinedCourseSettingsSpy).toHaveBeenCalledOnce();
            expect(getCombinedCourseSettingsSpy).toHaveBeenCalledWith(10);
            expect(result).toEqual({});
        });
    });

    describe('loadIrisSettingsObservable', () => {
        it('should call getGlobalSettings for GLOBAL', async () => {
            component.settingsType = IrisSettingsType.GLOBAL;

            const result = await firstValueFrom(component.loadIrisSettingsObservable());

            expect(getGlobalSettingsSpy).toHaveBeenCalledOnce();
            expect(result).toEqual({});
        });

        it('should call getUncombinedCourseSettings for COURSE', async () => {
            component.settingsType = IrisSettingsType.COURSE;
            component.courseId = 20;

            const result = await firstValueFrom(component.loadIrisSettingsObservable());

            expect(getUncombinedCourseSettingsSpy).toHaveBeenCalledOnce();
            expect(getUncombinedCourseSettingsSpy).toHaveBeenCalledWith(20);
            expect(result).toEqual({});
        });

        it('should call getUncombinedExerciseSettings for EXERCISE', async () => {
            component.settingsType = IrisSettingsType.EXERCISE;
            component.exerciseId = 30;

            const result = await firstValueFrom(component.loadIrisSettingsObservable());

            expect(getUncombinedExerciseSettingsSpy).toHaveBeenCalledOnce();
            expect(getUncombinedExerciseSettingsSpy).toHaveBeenCalledWith(30);
            expect(result).toEqual({});
        });
    });

    describe('saveIrisSettingsObservable', () => {
        beforeEach(() => {
            component.irisSettings = {} as IrisSettings;
        });

        it('should call setGlobalSettings for GLOBAL', async () => {
            component.settingsType = IrisSettingsType.GLOBAL;

            const httpResponse = await firstValueFrom(component.saveIrisSettingsObservable());

            expect(setGlobalSettingsSpy).toHaveBeenCalledOnce();
            expect(setGlobalSettingsSpy).toHaveBeenCalledWith({});
            expect(httpResponse.body).toEqual({});
        });

        it('should call setCourseSettings for COURSE', async () => {
            component.settingsType = IrisSettingsType.COURSE;
            component.courseId = 40;

            const httpResponse = await firstValueFrom(component.saveIrisSettingsObservable());

            expect(setCourseSettingsSpy).toHaveBeenCalledOnce();
            expect(setCourseSettingsSpy).toHaveBeenCalledWith(40, {});
            expect(httpResponse.body).toEqual({});
        });

        it('should call setExerciseSettings for EXERCISE', async () => {
            component.settingsType = IrisSettingsType.EXERCISE;
            component.exerciseId = 50;

            const httpResponse = await firstValueFrom(component.saveIrisSettingsObservable());

            expect(setExerciseSettingsSpy).toHaveBeenCalledOnce();
            expect(setExerciseSettingsSpy).toHaveBeenCalledWith(50, {});
            expect(httpResponse.body).toEqual({});
        });
    });

    describe('loadIrisSettings', () => {
        it('should handle error when settings are not available', fakeAsync(() => {
            getGlobalSettingsSpy.mockReturnValue(of(undefined));
            component.settingsType = IrisSettingsType.GLOBAL;
            const alertService = TestBed.inject(AlertService);
            const alertSpy = jest.spyOn(alertService, 'error');

            component.loadIrisSettings();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.noSettings');
            expect(component.isLoading).toBeFalse();
        }));

        it('should handle error when parent settings are not available', fakeAsync(() => {
            getUncombinedCourseSettingsSpy.mockReturnValue(of(mockSettings()));
            getGlobalSettingsSpy.mockReturnValue(of(undefined));
            component.settingsType = IrisSettingsType.COURSE;
            component.courseId = 1;
            const alertService = TestBed.inject(AlertService);
            const alertSpy = jest.spyOn(alertService, 'error');

            component.loadIrisSettings();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.noParentSettings');
        }));

        it('should set autoLectureIngestion and autoFaqIngestion flags', fakeAsync(() => {
            const settings = mockSettings();
            settings.irisLectureIngestionSettings = { autoIngestOnLectureAttachmentUpload: false, type: undefined as any, enabled: true };
            settings.irisFaqIngestionSettings = { autoIngestOnFaqCreation: false, type: undefined as any, enabled: true };
            getGlobalSettingsSpy.mockReturnValue(of(settings));
            component.settingsType = IrisSettingsType.GLOBAL;

            component.loadIrisSettings();
            tick();

            expect(component.autoLectureIngestion).toBeFalse();
            expect(component.autoFaqIngestion).toBeFalse();
        }));

        it('should default autoLectureIngestion and autoFaqIngestion to true when undefined', fakeAsync(() => {
            const settings = mockSettings();
            settings.irisLectureIngestionSettings = undefined;
            settings.irisFaqIngestionSettings = undefined;
            getGlobalSettingsSpy.mockReturnValue(of(settings));
            component.settingsType = IrisSettingsType.GLOBAL;

            component.loadIrisSettings();
            tick();

            expect(component.autoLectureIngestion).toBeTrue();
            expect(component.autoFaqIngestion).toBeTrue();
        }));

        it('should set isDirty to false after loading', fakeAsync(() => {
            const settings = mockSettings();
            getGlobalSettingsSpy.mockReturnValue(of(settings));
            component.settingsType = IrisSettingsType.GLOBAL;
            component.isDirty = true;

            component.loadIrisSettings();
            tick();

            expect(component.isDirty).toBeFalse();
        }));
    });

    describe('saveIrisSettings', () => {
        let alertService: AlertService;

        beforeEach(() => {
            alertService = TestBed.inject(AlertService);
            component.irisSettings = mockSettings();
            component.irisSettings.irisLectureIngestionSettings = { autoIngestOnLectureAttachmentUpload: true, type: undefined as any, enabled: true };
            component.irisSettings.irisFaqIngestionSettings = { autoIngestOnFaqCreation: true, type: undefined as any, enabled: true };
        });

        it('should set ingestion flags before saving', fakeAsync(() => {
            component.autoLectureIngestion = false;
            component.autoFaqIngestion = false;
            component.settingsType = IrisSettingsType.GLOBAL;

            component.saveIrisSettings();

            expect(setGlobalSettingsSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    irisLectureIngestionSettings: expect.objectContaining({
                        autoIngestOnLectureAttachmentUpload: false,
                    }),
                    irisFaqIngestionSettings: expect.objectContaining({
                        autoIngestOnFaqCreation: false,
                    }),
                }),
            );
        }));

        it('should handle save error with custom message', fakeAsync(() => {
            component.settingsType = IrisSettingsType.GLOBAL;
            const errorResponse = { status: 400, error: { message: 'Custom error message' } };
            setGlobalSettingsSpy.mockReturnValue(throwError(() => errorResponse));
            const alertSpy = jest.spyOn(alertService, 'error');

            component.saveIrisSettings();
            tick();

            expect(component.isSaving).toBeFalse();
            expect(alertSpy).toHaveBeenCalledWith('Custom error message');
        }));

        it('should handle generic save error', fakeAsync(() => {
            component.settingsType = IrisSettingsType.GLOBAL;
            const errorResponse = { status: 500 };
            setGlobalSettingsSpy.mockReturnValue(throwError(() => errorResponse));
            const alertSpy = jest.spyOn(alertService, 'error');

            component.saveIrisSettings();
            tick();

            expect(component.isSaving).toBeFalse();
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.save');
        }));

        it('should successfully save and update settings', fakeAsync(() => {
            component.settingsType = IrisSettingsType.GLOBAL;
            const updatedSettings = mockSettings();
            setGlobalSettingsSpy.mockReturnValue(of(new HttpResponse({ body: updatedSettings })));
            const alertSpy = jest.spyOn(alertService, 'success');

            component.saveIrisSettings();
            tick();

            expect(component.isSaving).toBeFalse();
            expect(component.isDirty).toBeFalse();
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.success');
        }));

        it('should handle undefined ingestion settings when saving', fakeAsync(() => {
            component.irisSettings = mockSettings();
            component.irisSettings.irisLectureIngestionSettings = undefined;
            component.irisSettings.irisFaqIngestionSettings = undefined;
            component.settingsType = IrisSettingsType.GLOBAL;
            setGlobalSettingsSpy.mockReturnValue(of(new HttpResponse({ body: mockSettings() })));

            component.saveIrisSettings();
            tick();

            expect(component.isSaving).toBeFalse();
        }));

        it('should handle null response body when saving', fakeAsync(() => {
            component.settingsType = IrisSettingsType.GLOBAL;
            setGlobalSettingsSpy.mockReturnValue(of(new HttpResponse({ body: null })));
            const alertSpy = jest.spyOn(alertService, 'success');

            component.saveIrisSettings();
            tick();

            expect(component.irisSettings).toBeUndefined();
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.success');
        }));
    });

    describe('ngDoCheck', () => {
        it('should set isDirty to true when settings change', () => {
            component.irisSettings = mockSettings();
            component.originalIrisSettings = mockSettings();
            component.isDirty = false;

            if (component.irisSettings!.irisProgrammingExerciseChatSettings) {
                component.irisSettings!.irisProgrammingExerciseChatSettings.enabled = false;
            }

            component.ngDoCheck();

            expect(component.isDirty).toBeTrue();
        });

        it('should not set isDirty when settings are equal', () => {
            component.irisSettings = mockSettings();
            component.originalIrisSettings = mockSettings();
            component.isDirty = false;

            component.ngDoCheck();

            expect(component.isDirty).toBeFalse();
        });
    });

    describe('ngOnInit', () => {
        it('should load settings and subscribe to feature toggle', fakeAsync(() => {
            component.settingsType = IrisSettingsType.GLOBAL;
            getGlobalSettingsSpy.mockReturnValue(of(mockSettings()));

            component.ngOnInit();
            tick();

            expect(component.tutorSuggestionFeatureEnabled).toBeTrue();
            expect(component.featureToggleSubscription).toBeDefined();
        }));
    });

    describe('ngOnDestroy', () => {
        it('should unsubscribe from feature toggle subscription', () => {
            const mockSubscription = { unsubscribe: jest.fn() };
            component.featureToggleSubscription = mockSubscription as any;

            component.ngOnDestroy();

            expect(mockSubscription.unsubscribe).toHaveBeenCalledOnce();
        });

        it('should handle undefined subscription', () => {
            component.featureToggleSubscription = undefined;

            expect(() => component.ngOnDestroy()).not.toThrow();
        });
    });

    describe('canDeactivate', () => {
        it('should return true when not dirty', () => {
            component.isDirty = false;

            expect(component.canDeactivate()).toBeTrue();
        });

        it('should return false when dirty', () => {
            component.isDirty = true;

            expect(component.canDeactivate()).toBeFalse();
        });
    });

    describe('loadParentIrisSettingsObservable for GLOBAL', () => {
        it('should return empty observable for GLOBAL settings', async () => {
            component.settingsType = IrisSettingsType.GLOBAL;

            const observable = component.loadParentIrisSettingsObservable();

            observable.subscribe((result) => {
                expect(result).toBeUndefined();
            });
        });
    });
});
