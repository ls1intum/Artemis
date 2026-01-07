import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { IrisCourseSettingsDTO, IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ActivatedRoute, Params } from '@angular/router';

describe('IrisSettingsUpdateComponent', () => {
    let component: IrisSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    let alertService: AlertService;
    let accountService: AccountService;
    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: '1' });
    let getCourseSettingsSpy: jest.SpyInstance;

    const mockSettings: IrisCourseSettingsDTO = {
        enabled: true,
        customInstructions: 'Test instructions',
        variant: 'default',
        rateLimit: { requests: 100, timeframeHours: 24 },
    };

    const mockResponse: IrisCourseSettingsWithRateLimitDTO = {
        courseId: 1,
        settings: mockSettings,
        effectiveRateLimit: { requests: 100, timeframeHours: 24 },
        applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockJhiTranslateDirective, IrisSettingsUpdateComponent, FaIconComponent],
            declarations: [MockPipe(ArtemisTranslatePipe), MockComponent(ButtonComponent)],
            providers: [
                {
                    provide: IrisSettingsService,
                    useValue: {
                        getCourseSettingsWithRateLimit: jest.fn().mockReturnValue(of(mockResponse)),
                        updateCourseSettings: jest.fn().mockReturnValue(of(new HttpResponse({ body: mockResponse }))),
                    },
                },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: { params: routeParamsSubject.asObservable() } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisSettingsUpdateComponent);
        component = fixture.componentInstance;
        irisSettingsService = TestBed.inject(IrisSettingsService);
        alertService = TestBed.inject(AlertService);
        accountService = TestBed.inject(AccountService);

        // Store spy reference so tests can override return values
        getCourseSettingsSpy = irisSettingsService.getCourseSettingsWithRateLimit as jest.Mock;
        jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
    });

    afterEach(() => {
        // Clear call counts but not implementations
        (irisSettingsService.getCourseSettingsWithRateLimit as jest.Mock).mockClear();
        (irisSettingsService.updateCourseSettings as jest.Mock).mockClear();
    });

    it('should create', () => {
        expect(component).toBeDefined();
    });

    describe('ngOnInit', () => {
        it('should load settings from route params', fakeAsync(() => {
            routeParamsSubject.next({ courseId: '1' });
            const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit');

            component.ngOnInit();
            tick();

            expect(component.courseId).toBe(1);
            expect(getCourseSettingsSpy).toHaveBeenCalledOnce();
            expect(getCourseSettingsSpy).toHaveBeenCalledWith(1);
            expect(component.settings()).toEqual(mockSettings);
            expect(component.rateLimitRequests()).toBe(100);
            expect(component.rateLimitTimeframeHours()).toBe(24);
            expect(component.effectiveRateLimit()).toEqual({ requests: 100, timeframeHours: 24 });
            expect(component.applicationDefaults()).toEqual({ requests: 50, timeframeHours: 12 });
            expect(component.isDirty()).toBeFalse();
        }));

        it('should handle null rateLimit from server', fakeAsync(() => {
            const nullRateLimitResponse: IrisCourseSettingsWithRateLimitDTO = {
                courseId: 1,
                settings: { enabled: true, variant: 'default', rateLimit: undefined as any },
                effectiveRateLimit: { requests: 50, timeframeHours: 12 },
                applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
            };
            getCourseSettingsSpy.mockReturnValue(of(nullRateLimitResponse));
            routeParamsSubject.next({ courseId: '1' });

            component.ngOnInit();
            tick();

            expect(component.rateLimitRequests()).toBeUndefined();
            expect(component.rateLimitTimeframeHours()).toBeUndefined();
        }));

        it('should set isAdmin based on account service', () => {
            expect(component.isAdmin).toBeTrue(); // Default mock returns true
        });
    });

    describe('loadSettings', () => {
        it('should show error if no courseId', fakeAsync(() => {
            const alertSpy = jest.spyOn(alertService, 'error');
            component.courseId = undefined;

            component.loadSettings();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.noCourseId');
        }));

        it('should show error if response is undefined', fakeAsync(() => {
            getCourseSettingsSpy.mockReturnValue(of(undefined));
            const alertSpy = jest.spyOn(alertService, 'error');
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.noSettings');
        }));

        it('should handle load error', fakeAsync(() => {
            getCourseSettingsSpy.mockReturnValue(throwError(() => new Error('Load failed')));
            const alertSpy = jest.spyOn(alertService, 'error');
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.load');
            expect(component.isLoading()).toBeFalse();
        }));

        it('should set loading states correctly', fakeAsync(() => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
            expect(component.isLoading()).toBeFalse();
        }));
    });

    describe('saveSettings', () => {
        beforeEach(fakeAsync(() => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
            fixture.detectChanges();
        }));

        it('should save settings as admin with rate limit from form fields', fakeAsync(() => {
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            component.isAdmin = true;
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            const alertSpy = jest.spyOn(alertService, 'success');
            tick();

            // Modify form fields (use spread to avoid mutating shared mock)
            component.settings.set({ ...component.settings()!, enabled: false });
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(48);

            component.saveSettings();
            tick();

            expect(updateSpy).toHaveBeenCalledOnce();
            const savedSettings = updateSpy.mock.calls[0][1];
            expect(savedSettings.enabled).toBeFalse();
            expect(savedSettings.rateLimit).toEqual({ requests: 200, timeframeHours: 48 });
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.success');
            expect(component.isDirty()).toBeFalse();
            expect(component.isSaving()).toBeFalse();
        }));

        it('should send undefined rateLimit when both fields are cleared (revert to defaults)', fakeAsync(() => {
            // Course already has explicit rate limits from mockSettings
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            component.isAdmin = true;
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            tick();

            // Admin clears both fields to revert to application defaults
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            component.saveSettings();
            tick();

            // Should send undefined rateLimit to use application defaults
            const savedSettings = updateSpy.mock.calls[0][1];
            expect(savedSettings.rateLimit).toBeUndefined();
        }));

        it('should send rateLimit object when admin enters values', fakeAsync(() => {
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            component.isAdmin = true;
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            tick();

            // Admin enters explicit values
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(48);

            component.saveSettings();
            tick();

            // Should send explicit rateLimit object
            const savedSettings = updateSpy.mock.calls[0][1];
            expect(savedSettings.rateLimit).toEqual({ requests: 200, timeframeHours: 48 });
        }));

        it('should not allow non-admins to change variant', fakeAsync(() => {
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(false);
            component.isAdmin = false;
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            tick();

            // Try to change variant as non-admin
            component.settings.set({ ...mockSettings, variant: 'advanced' });

            component.saveSettings();
            tick();

            // Variant should be restored to original
            const callArgs = updateSpy.mock.calls[0];
            expect(callArgs[1].variant).toBe('default');
        }));

        it('should not allow non-admins to change rate limits', fakeAsync(() => {
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(false);
            component.isAdmin = false;
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            tick();

            // Try to change rate limits as non-admin (even though UI shouldn't show these fields)
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(48);

            component.saveSettings();
            tick();

            // Rate limits should be restored to original from originalSettings
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
            expect(component.isSaving()).toBeFalse();
        }));

        it('should handle save error without message', fakeAsync(() => {
            jest.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(throwError(() => new Error('Save failed')));
            const alertSpy = jest.spyOn(alertService, 'error');
            tick();

            component.saveSettings();
            tick();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.save');
            expect(component.isSaving()).toBeFalse();
        }));

        it('should do nothing if no courseId', () => {
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings');
            component.courseId = undefined;

            component.saveSettings();

            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should do nothing if no settings', () => {
            const updateSpy = jest.spyOn(irisSettingsService, 'updateCourseSettings');
            component.settings.set(undefined);

            component.saveSettings();

            expect(updateSpy).not.toHaveBeenCalled();
        });
    });

    describe('setEnabled', () => {
        const initComponent = () => {
            (irisSettingsService.getCourseSettingsWithRateLimit as jest.Mock).mockReturnValue(of(mockResponse));
            (irisSettingsService.updateCourseSettings as jest.Mock).mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
        };

        it('should toggle enabled state and auto-save', fakeAsync(() => {
            initComponent();

            // Override mock for this specific test
            (irisSettingsService.updateCourseSettings as jest.Mock).mockReturnValue(
                of(new HttpResponse({ body: { ...mockResponse, settings: { ...mockSettings, enabled: false } } })),
            );

            component.setEnabled(false);
            tick();

            expect(component.settings()!.enabled).toBeFalse();
            expect(irisSettingsService.updateCourseSettings).toHaveBeenCalledOnce();
        }));

        it('should not call save if enabled state is unchanged', fakeAsync(() => {
            initComponent();

            // Clear any previous calls
            (irisSettingsService.updateCourseSettings as jest.Mock).mockClear();

            // Get current enabled state and try to set it to the same value
            const currentEnabled = component.settings()!.enabled;
            component.setEnabled(currentEnabled);
            tick();

            // Should not trigger save when setting to same value
            expect(irisSettingsService.updateCourseSettings).not.toHaveBeenCalled();
        }));

        it('should revert on error', fakeAsync(() => {
            initComponent();

            // Store initial enabled state
            const initialEnabled = component.settings()!.enabled;

            (irisSettingsService.updateCourseSettings as jest.Mock).mockReturnValue(throwError(() => new Error('Save failed')));

            // Try to toggle - should revert on error
            component.setEnabled(!initialEnabled);
            tick();

            // Should revert back to initial state on error
            expect(component.settings()!.enabled).toBe(initialEnabled);
        }));

        it('should do nothing if settings are undefined', () => {
            // Reset component state to test undefined case
            component.settings.set(undefined);
            component['originalSettings'].set(undefined);
            expect(() => component.setEnabled(true)).not.toThrow();
        });
    });

    describe('getCustomInstructionsLength', () => {
        it('should return length of custom instructions', () => {
            component.settings.set({ ...mockSettings, customInstructions: 'Test' });
            expect(component.getCustomInstructionsLength()).toBe(4);

            component.settings.set({ ...mockSettings, customInstructions: 'Hello World' });
            expect(component.getCustomInstructionsLength()).toBe(11);
        });

        it('should return 0 if custom instructions are undefined', () => {
            component.settings.set({ ...mockSettings, customInstructions: undefined });
            expect(component.getCustomInstructionsLength()).toBe(0);
        });

        it('should return 0 if settings are undefined', () => {
            component.settings.set(undefined);
            expect(component.getCustomInstructionsLength()).toBe(0);
        });
    });

    describe('dirty checking', () => {
        const initComponent = () => {
            (irisSettingsService.getCourseSettingsWithRateLimit as jest.Mock).mockReturnValue(of(mockResponse));
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
        };

        it('should detect changes and set isDirty', fakeAsync(() => {
            initComponent();

            expect(component.isDirty()).toBeFalse();

            // Make a change to customInstructions
            component.settings.set({ ...component.settings()!, customInstructions: 'Changed instructions' });

            expect(component.isDirty()).toBeTrue();
        }));

        it('should not mark as dirty if no changes', fakeAsync(() => {
            initComponent();

            expect(component.isDirty()).toBeFalse();

            // No changes made - isDirty should still be false
            expect(component.isDirty()).toBeFalse();
        }));

        it('should detect rate limit changes and set isDirty', fakeAsync(() => {
            initComponent();

            expect(component.isDirty()).toBeFalse();

            // Change only rate limit fields
            component.rateLimitRequests.set(999);

            expect(component.isDirty()).toBeTrue();
        }));

        it('should reset isDirty to false when changes are reverted', fakeAsync(() => {
            initComponent();

            expect(component.isDirty()).toBeFalse();

            // Store the original value
            const originalInstructions = component.settings()!.customInstructions;

            // Make a change to customInstructions
            component.settings.set({ ...component.settings()!, customInstructions: 'Some completely different instructions' });
            expect(component.isDirty()).toBeTrue();

            // Revert the change to original
            component.settings.set({ ...component.settings()!, customInstructions: originalInstructions });
            expect(component.isDirty()).toBeFalse();
        }));
    });

    describe('canDeactivate', () => {
        it('should allow deactivation when not dirty', fakeAsync(() => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
            // No changes made, isDirty should be false
            expect(component.canDeactivate()).toBeTrue();
        }));

        it('should prevent deactivation when dirty', fakeAsync(() => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
            // Make a change to trigger dirty state
            component.settings.set({ ...component.settings()!, customInstructions: 'Changed' });
            expect(component.canDeactivate()).toBeFalse();
        }));
    });

    describe('CUSTOM_INSTRUCTIONS_MAX_LENGTH', () => {
        it('should be set to 2048', () => {
            expect(component.CUSTOM_INSTRUCTIONS_MAX_LENGTH).toBe(2048);
        });
    });

    describe('rate limit validation', () => {
        beforeEach(fakeAsync(() => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
        }));

        it('should be valid when both fields are empty (use defaults)', fakeAsync(() => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBeTrue();
        }));

        it('should be valid when both fields are filled with valid values', fakeAsync(() => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(24);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBeTrue();
        }));

        it('should be valid when requests is 0 and timeframe is positive', fakeAsync(() => {
            component.rateLimitRequests.set(0);
            component.rateLimitTimeframeHours.set(24);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBeTrue();
        }));

        it('should mark timeframe field as invalid when only requests is filled', fakeAsync(() => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBe('artemisApp.iris.settings.rateLimitValidation.bothRequired');
            expect(component.isFormValid()).toBeFalse();
        }));

        it('should mark requests field as invalid when only timeframe is filled', fakeAsync(() => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(24);

            expect(component.rateLimitRequestsError()).toBe('artemisApp.iris.settings.rateLimitValidation.bothRequired');
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBeFalse();
        }));

        it('should mark requests field as invalid when requests is negative', fakeAsync(() => {
            component.rateLimitRequests.set(-1);
            component.rateLimitTimeframeHours.set(24);

            expect(component.rateLimitRequestsError()).toBe('artemisApp.iris.settings.rateLimitValidation.requestsNonNegative');
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBeFalse();
        }));

        it('should mark timeframe field as invalid when timeframe is zero', fakeAsync(() => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(0);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBe('artemisApp.iris.settings.rateLimitValidation.timeframePositive');
            expect(component.isFormValid()).toBeFalse();
        }));

        it('should mark timeframe field as invalid when timeframe is negative', fakeAsync(() => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(-1);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBe('artemisApp.iris.settings.rateLimitValidation.timeframePositive');
            expect(component.isFormValid()).toBeFalse();
        }));

        it('should handle empty string as empty value for requests', fakeAsync(() => {
            component.rateLimitRequests.set('' as unknown as number);
            component.rateLimitTimeframeHours.set(undefined);

            // Both effectively empty, should be valid
            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBeTrue();
        }));

        it('should handle empty string as empty value for timeframe', fakeAsync(() => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set('' as unknown as number);

            // Both effectively empty, should be valid
            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBeTrue();
        }));

        it('should mark both fields as invalid when both have errors', fakeAsync(() => {
            component.rateLimitRequests.set(-5);
            component.rateLimitTimeframeHours.set(-3);

            expect(component.rateLimitRequestsError()).toBe('artemisApp.iris.settings.rateLimitValidation.requestsNonNegative');
            expect(component.rateLimitTimeframeError()).toBe('artemisApp.iris.settings.rateLimitValidation.timeframePositive');
            expect(component.isFormValid()).toBeFalse();
        }));
    });

    describe('effective rate limit preview', () => {
        beforeEach(fakeAsync(() => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
        }));

        it('should return application defaults when both fields are empty', fakeAsync(() => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 50, timeframeHours: 12 });
        }));

        it('should return entered values when both fields are filled', fakeAsync(() => {
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(48);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 200, timeframeHours: 48 });
        }));

        it('should merge entered values with defaults when only requests is filled', fakeAsync(() => {
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 200, timeframeHours: 12 });
        }));

        it('should merge entered values with defaults when only timeframe is filled', fakeAsync(() => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(48);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 50, timeframeHours: 48 });
        }));

        it('should handle zero requests as a valid value', fakeAsync(() => {
            component.rateLimitRequests.set(0);
            component.rateLimitTimeframeHours.set(24);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 0, timeframeHours: 24 });
        }));

        it('should treat empty string as empty value', fakeAsync(() => {
            component.rateLimitRequests.set('' as unknown as number);
            component.rateLimitTimeframeHours.set('' as unknown as number);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 50, timeframeHours: 12 });
        }));
    });

    describe('isEffectiveRateLimitUnlimited', () => {
        beforeEach(fakeAsync(() => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
        }));

        it('should return false when defaults have values', fakeAsync(() => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);
            // applicationDefaults is { requests: 50, timeframeHours: 12 }

            expect(component.isEffectiveRateLimitUnlimited()).toBeFalse();
        }));

        it('should return false when explicit values are set', fakeAsync(() => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(24);

            expect(component.isEffectiveRateLimitUnlimited()).toBeFalse();
        }));

        it('should return true when defaults are null', fakeAsync(() => {
            // Simulate unlimited defaults
            component.applicationDefaults.set({ requests: undefined, timeframeHours: undefined });
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.isEffectiveRateLimitUnlimited()).toBeTrue();
        }));
    });

    describe('hasEffectiveRequestsLimit and hasEffectiveTimeframeLimit', () => {
        beforeEach(fakeAsync(() => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            tick();
        }));

        it('should return true when defaults have values and fields are empty', fakeAsync(() => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.hasEffectiveRequestsLimit()).toBeTrue();
            expect(component.hasEffectiveTimeframeLimit()).toBeTrue();
        }));

        it('should return true when explicit values are set', fakeAsync(() => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(24);

            expect(component.hasEffectiveRequestsLimit()).toBeTrue();
            expect(component.hasEffectiveTimeframeLimit()).toBeTrue();
        }));

        it('should return true for requests when requests is 0', fakeAsync(() => {
            component.rateLimitRequests.set(0);
            component.rateLimitTimeframeHours.set(24);

            expect(component.hasEffectiveRequestsLimit()).toBeTrue();
        }));

        it('should return false when defaults are null and fields are empty', fakeAsync(() => {
            component.applicationDefaults.set({ requests: undefined, timeframeHours: undefined });
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.hasEffectiveRequestsLimit()).toBeFalse();
            expect(component.hasEffectiveTimeframeLimit()).toBeFalse();
        }));
    });
});
