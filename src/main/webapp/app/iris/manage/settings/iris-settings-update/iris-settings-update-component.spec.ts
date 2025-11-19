import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { CourseIrisSettingsDTO, IrisCourseSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { of, throwError } from 'rxjs';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('IrisSettingsUpdateComponent', () => {
    let component: IrisSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    let alertService: AlertService;
    let accountService: AccountService;

    const mockSettings: IrisCourseSettingsDTO = {
        enabled: true,
        customInstructions: 'Test instructions',
        variant: 'DEFAULT',
        rateLimit: { requests: 100, timeframeHours: 24 },
    };

    const mockResponse: CourseIrisSettingsDTO = {
        courseId: 1,
        settings: mockSettings,
        effectiveRateLimit: { requests: 100, timeframeHours: 24 },
        applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
    };

    const mockVariants = ['DEFAULT', 'ADVANCED'];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockJhiTranslateDirective, IrisSettingsUpdateComponent],
            declarations: [MockPipe(ArtemisTranslatePipe), MockComponent(ButtonComponent)],
            providers: [
                MockProvider(IrisSettingsService),
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisSettingsUpdateComponent);
        component = fixture.componentInstance;
        irisSettingsService = TestBed.inject(IrisSettingsService);
        alertService = TestBed.inject(AlertService);
        accountService = TestBed.inject(AccountService);

        // Setup default mocks
        jest.spyOn(irisSettingsService, 'getCourseSettings').mockReturnValue(of(mockResponse));
        jest.spyOn(irisSettingsService, 'getVariants').mockReturnValue(of(mockVariants as any));
        jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeDefined();
    });

    describe('ngOnInit', () => {
        it('should load settings and variants', fakeAsync(() => {
            component.courseId = 1;
            const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettings');
            const getVariantsSpy = jest.spyOn(irisSettingsService, 'getVariants');

            component.ngOnInit();
            tick();

            expect(getCourseSettingsSpy).toHaveBeenCalledOnce();
            expect(getCourseSettingsSpy).toHaveBeenCalledWith(1);
            expect(getVariantsSpy).toHaveBeenCalledOnce();
            expect(component.settings).toEqual(mockSettings);
            expect(component.effectiveRateLimit).toEqual({ requests: 100, timeframeHours: 24 });
            expect(component.applicationDefaults).toEqual({ requests: 50, timeframeHours: 12 });
            expect(component.availableVariants).toEqual(mockVariants);
            expect(component.isDirty).toBeFalse();
        }));

        it('should set isAdmin based on account service', () => {
            expect(component.isAdmin).toBeTrue(); // Default mock returns true
        });
    });

    describe('loadSettings', () => {
        it('should show error if no courseId', fakeAsync(() => {
            const alertSpy = jest.spyOn(alertService, 'error');
            component.courseId = undefined as any;

            component.loadSettings();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.noCourseId');
        }));

        it('should show error if response is undefined', fakeAsync(() => {
            jest.spyOn(irisSettingsService, 'getCourseSettings').mockReturnValue(of(undefined));
            const alertSpy = jest.spyOn(alertService, 'error');
            component.courseId = 1;

            component.loadSettings();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.noSettings');
        }));

        it('should handle load error', fakeAsync(() => {
            jest.spyOn(irisSettingsService, 'getCourseSettings').mockReturnValue(throwError(() => new Error('Load failed')));
            const alertSpy = jest.spyOn(alertService, 'error');
            component.courseId = 1;

            component.loadSettings();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.load');
            expect(component.isLoading).toBeFalse();
        }));

        it('should set loading states correctly', fakeAsync(() => {
            component.courseId = 1;

            component.loadSettings();
            // isLoading is set synchronously before the async call
            tick(); // Wait for async operations
            expect(component.isLoading).toBeFalse();
        }));
    });

    describe('loadVariants', () => {
        it('should load variants successfully', fakeAsync(() => {
            component.loadVariants();
            tick();

            expect(component.availableVariants).toEqual(mockVariants);
        }));

        it('should handle variant load error silently', fakeAsync(() => {
            jest.spyOn(irisSettingsService, 'getVariants').mockReturnValue(throwError(() => new Error('Variants failed')));

            component.loadVariants();
            tick();

            // Should not crash or show alert
            expect(component.availableVariants).toEqual([]);
        }));
    });

    describe('saveSettings', () => {
        beforeEach(() => {
            component.courseId = 1;
            component.ngOnInit();
            fixture.detectChanges();
        });

        it('should save settings as admin', fakeAsync(() => {
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            component.isAdmin = true;
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            const alertSpy = jest.spyOn(alertService, 'success');
            tick();

            const updatedSettings = { ...mockSettings, enabled: false };
            component.settings = updatedSettings;

            component.saveSettings();
            tick();

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledWith(1, updatedSettings);
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.success');
            expect(component.isDirty).toBeFalse();
            expect(component.isSaving).toBeFalse();
        }));

        it('should not allow non-admins to change variant', fakeAsync(() => {
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(false);
            component.isAdmin = false;
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            tick();

            // Try to change variant as non-admin
            component.settings = { ...mockSettings, variant: 'ADVANCED' };

            component.saveSettings();
            tick();

            // Variant should be restored to original
            const callArgs = updateSpy.mock.calls[0];
            expect(callArgs[1].variant).toBe('DEFAULT');
        }));

        it('should not allow non-admins to change rate limits', fakeAsync(() => {
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(false);
            component.isAdmin = false;
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            tick();

            // Try to change rate limits as non-admin
            component.settings = { ...mockSettings, rateLimit: { requests: 200, timeframeHours: 48 } };

            component.saveSettings();
            tick();

            // Rate limits should be restored to original
            const callArgs = updateSpy.mock.calls[0];
            expect(callArgs[1].rateLimit).toEqual({ requests: 100, timeframeHours: 24 });
        }));

        it('should handle save error with message', fakeAsync(() => {
            const errorResponse = new HttpErrorResponse({
                status: 400,
                error: { message: 'Custom error message' },
            });
            jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(throwError(() => errorResponse));
            const alertSpy = jest.spyOn(alertService, 'error');
            tick();

            component.saveSettings();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('Custom error message');
            expect(component.isSaving).toBeFalse();
        }));

        it('should handle save error without message', fakeAsync(() => {
            jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(throwError(() => new Error('Save failed')));
            const alertSpy = jest.spyOn(alertService, 'error');
            tick();

            component.saveSettings();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.save');
            expect(component.isSaving).toBeFalse();
        }));

        it('should do nothing if no courseId', () => {
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings');
            component.courseId = undefined as any;

            component.saveSettings();

            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should do nothing if no settings', () => {
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings');
            component.settings = undefined;

            component.saveSettings();

            expect(updateSpy).not.toHaveBeenCalled();
        });
    });

    describe('setEnabled', () => {
        it('should toggle enabled state', () => {
            component.settings = { ...mockSettings, enabled: true };

            component.setEnabled(false);
            expect(component.settings.enabled).toBeFalse();

            component.setEnabled(true);
            expect(component.settings.enabled).toBeTrue();
        });

        it('should do nothing if settings are undefined', () => {
            component.settings = undefined;
            expect(() => component.setEnabled(true)).not.toThrow();
        });
    });

    describe('getCustomInstructionsLength', () => {
        it('should return length of custom instructions', () => {
            component.settings = { ...mockSettings, customInstructions: 'Test' };
            expect(component.getCustomInstructionsLength()).toBe(4);

            component.settings = { ...mockSettings, customInstructions: 'Hello World' };
            expect(component.getCustomInstructionsLength()).toBe(11);
        });

        it('should return 0 if custom instructions are undefined', () => {
            component.settings = { ...mockSettings, customInstructions: undefined };
            expect(component.getCustomInstructionsLength()).toBe(0);
        });

        it('should return 0 if settings are undefined', () => {
            component.settings = undefined;
            expect(component.getCustomInstructionsLength()).toBe(0);
        });
    });

    describe('dirty checking', () => {
        it('should detect changes and set isDirty', fakeAsync(() => {
            component.courseId = 1;
            component.ngOnInit();
            tick();

            expect(component.isDirty).toBeFalse();

            // Make a change
            component.settings!.enabled = false;
            component.ngDoCheck();

            expect(component.isDirty).toBeTrue();
        }));

        it('should not mark as dirty if no changes', fakeAsync(() => {
            component.courseId = 1;
            component.ngOnInit();
            tick();

            expect(component.isDirty).toBeFalse();

            component.ngDoCheck();

            expect(component.isDirty).toBeFalse();
        }));
    });

    describe('canDeactivate', () => {
        it('should allow deactivation when not dirty', () => {
            component.isDirty = false;
            expect(component.canDeactivate()).toBeTrue();
        });

        it('should prevent deactivation when dirty', () => {
            component.isDirty = true;
            expect(component.canDeactivate()).toBeFalse();
        });
    });

    describe('CUSTOM_INSTRUCTIONS_MAX_LENGTH', () => {
        it('should be set to 2048', () => {
            expect(component.CUSTOM_INSTRUCTIONS_MAX_LENGTH).toBe(2048);
        });
    });
});
