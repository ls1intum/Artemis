import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
    setupTestBed({ zoneless: true });

    let component: IrisSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    let alertService: AlertService;
    let accountService: AccountService;
    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: '1' });
    let getCourseSettingsSpy: ReturnType<typeof vi.fn>;

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
            imports: [MockJhiTranslateDirective, IrisSettingsUpdateComponent, FaIconComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ButtonComponent)],
            providers: [
                {
                    provide: IrisSettingsService,
                    useValue: {
                        getCourseSettingsWithRateLimit: vi.fn().mockReturnValue(of(mockResponse)),
                        updateCourseSettings: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockResponse }))),
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
        getCourseSettingsSpy = irisSettingsService.getCourseSettingsWithRateLimit as ReturnType<typeof vi.fn>;
        vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeDefined();
    });

    describe('ngOnInit', () => {
        it('should load settings from route params', async () => {
            routeParamsSubject.next({ courseId: '1' });
            const localGetCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit');

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.courseId).toBe(1);
            expect(localGetCourseSettingsSpy).toHaveBeenCalled();
            expect(localGetCourseSettingsSpy).toHaveBeenCalledWith(1);
            expect(component.settings()).toEqual(mockSettings);
            expect(component.rateLimitRequests()).toBe(100);
            expect(component.rateLimitTimeframeHours()).toBe(24);
            expect(component.effectiveRateLimit()).toEqual({ requests: 100, timeframeHours: 24 });
            expect(component.applicationDefaults()).toEqual({ requests: 50, timeframeHours: 12 });
            expect(component.isDirty()).toBe(false);
        });

        it('should handle null rateLimit from server', async () => {
            const nullRateLimitResponse: IrisCourseSettingsWithRateLimitDTO = {
                courseId: 1,
                settings: { enabled: true, variant: 'default', rateLimit: undefined as any },
                effectiveRateLimit: { requests: 50, timeframeHours: 12 },
                applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
            };
            getCourseSettingsSpy.mockReturnValue(of(nullRateLimitResponse));
            routeParamsSubject.next({ courseId: '1' });

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.rateLimitRequests()).toBeUndefined();
            expect(component.rateLimitTimeframeHours()).toBeUndefined();
        });

        it('should set isAdmin based on account service', () => {
            expect(component.isAdmin()).toBe(true); // Default mock returns true
        });
    });

    describe('loadSettings', () => {
        it('should show error if no courseId', async () => {
            const alertSpy = vi.spyOn(alertService, 'error');
            component.courseId = undefined;

            component.loadSettings();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.noCourseId');
        });

        it('should show error if response is undefined', async () => {
            getCourseSettingsSpy.mockReturnValue(of(undefined));
            const alertSpy = vi.spyOn(alertService, 'error');
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.noSettings');
        });

        it('should handle load error', async () => {
            getCourseSettingsSpy.mockReturnValue(throwError(() => new Error('Load failed')));
            const alertSpy = vi.spyOn(alertService, 'error');
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.load');
            expect(component.isLoading()).toBe(false);
        });

        it('should set loading states correctly', async () => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
            expect(component.isLoading()).toBe(false);
        });
    });

    describe('saveSettings', () => {
        beforeEach(async () => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
            fixture.detectChanges();
        });

        it('should save settings as admin with rate limit from form fields', async () => {
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            component.isAdmin.set(true);
            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            const alertSpy = vi.spyOn(alertService, 'success');
            await fixture.whenStable();

            // Modify form fields (use spread to avoid mutating shared mock)
            component.settings.set({ ...component.settings()!, enabled: false });
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(48);

            component.saveSettings();
            await fixture.whenStable();

            expect(updateSpy).toHaveBeenCalledOnce();
            const savedSettings = updateSpy.mock.calls[0][1];
            expect(savedSettings.enabled).toBe(false);
            expect(savedSettings.rateLimit).toEqual({ requests: 200, timeframeHours: 48 });
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.success');
            expect(component.isDirty()).toBe(false);
            expect(component.isSaving()).toBe(false);
        });

        it('should send undefined rateLimit when both fields are cleared (revert to defaults)', async () => {
            // Course already has explicit rate limits from mockSettings
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            component.isAdmin.set(true);
            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            await fixture.whenStable();

            // Admin clears both fields to revert to application defaults
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            component.saveSettings();
            await fixture.whenStable();

            // Should send undefined rateLimit to use application defaults
            const savedSettings = updateSpy.mock.calls[0][1];
            expect(savedSettings.rateLimit).toBeUndefined();
        });

        it('should send rateLimit object when admin enters values', async () => {
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            component.isAdmin.set(true);
            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            await fixture.whenStable();

            // Admin enters explicit values
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(48);

            component.saveSettings();
            await fixture.whenStable();

            // Should send explicit rateLimit object
            const savedSettings = updateSpy.mock.calls[0][1];
            expect(savedSettings.rateLimit).toEqual({ requests: 200, timeframeHours: 48 });
        });

        it('should not allow non-admins to change variant', async () => {
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(false);
            component.isAdmin.set(false);
            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            await fixture.whenStable();

            // Try to change variant as non-admin
            component.settings.set({ ...mockSettings, variant: 'advanced' });

            component.saveSettings();
            await fixture.whenStable();

            // Variant should be restored to original
            const callArgs = updateSpy.mock.calls[0];
            expect(callArgs[1].variant).toBe('default');
        });

        it('should not allow non-admins to change rate limits', async () => {
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(false);
            component.isAdmin.set(false);
            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            await fixture.whenStable();

            // Try to change rate limits as non-admin (even though UI shouldn't show these fields)
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(48);

            component.saveSettings();
            await fixture.whenStable();

            // Rate limits should be restored to original from originalSettings
            const callArgs = updateSpy.mock.calls[0];
            expect(callArgs[1].rateLimit).toEqual({ requests: 100, timeframeHours: 24 });
        });

        it('should handle save error with message', async () => {
            const errorResponse = new HttpErrorResponse({
                status: 400,
                error: { message: 'Custom error message' },
            });
            vi.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(throwError(() => errorResponse));
            const alertSpy = vi.spyOn(alertService, 'error');
            await fixture.whenStable();

            component.saveSettings();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalledWith('Custom error message');
            expect(component.isSaving()).toBe(false);
        });

        it('should handle save error without message', async () => {
            vi.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(throwError(() => new Error('Save failed')));
            const alertSpy = vi.spyOn(alertService, 'error');
            await fixture.whenStable();

            component.saveSettings();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.iris.settings.error.save');
            expect(component.isSaving()).toBe(false);
        });

        it('should do nothing if no courseId', () => {
            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings');
            component.courseId = undefined;

            component.saveSettings();

            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should do nothing if no settings', () => {
            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings');
            component.settings.set(undefined);

            component.saveSettings();

            expect(updateSpy).not.toHaveBeenCalled();
        });
    });

    describe('setEnabled', () => {
        const initComponent = async () => {
            (irisSettingsService.getCourseSettingsWithRateLimit as ReturnType<typeof vi.fn>).mockReturnValue(of(mockResponse));
            (irisSettingsService.updateCourseSettings as ReturnType<typeof vi.fn>).mockReturnValue(of(new HttpResponse({ body: mockResponse })));
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
        };

        it('should toggle enabled state and auto-save', async () => {
            await initComponent();

            // Override mock for this specific test
            (irisSettingsService.updateCourseSettings as ReturnType<typeof vi.fn>).mockReturnValue(
                of(new HttpResponse({ body: { ...mockResponse, settings: { ...mockSettings, enabled: false } } })),
            );

            component.setEnabled(false);
            await fixture.whenStable();

            expect(component.settings()!.enabled).toBe(false);
            expect(irisSettingsService.updateCourseSettings).toHaveBeenCalledOnce();
        });

        it('should not call save if enabled state is unchanged', async () => {
            await initComponent();

            // Clear any previous calls
            (irisSettingsService.updateCourseSettings as ReturnType<typeof vi.fn>).mockClear();

            // Get current enabled state and try to set it to the same value
            const currentEnabled = component.settings()!.enabled;
            component.setEnabled(currentEnabled);
            await fixture.whenStable();

            // Should not trigger save when setting to same value
            expect(irisSettingsService.updateCourseSettings).not.toHaveBeenCalled();
        });

        it('should revert on error', async () => {
            await initComponent();

            // Store initial enabled state
            const initialEnabled = component.settings()!.enabled;

            (irisSettingsService.updateCourseSettings as ReturnType<typeof vi.fn>).mockReturnValue(throwError(() => new Error('Save failed')));

            // Try to toggle - should revert on error
            component.setEnabled(!initialEnabled);
            await fixture.whenStable();

            // Should revert back to initial state on error
            expect(component.settings()!.enabled).toBe(initialEnabled);
        });

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
        const initComponent = async () => {
            (irisSettingsService.getCourseSettingsWithRateLimit as ReturnType<typeof vi.fn>).mockReturnValue(of(mockResponse));
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
        };

        it('should detect changes and set isDirty', async () => {
            await initComponent();

            expect(component.isDirty()).toBe(false);

            // Make a change to customInstructions
            component.settings.set({ ...component.settings()!, customInstructions: 'Changed instructions' });

            expect(component.isDirty()).toBe(true);
        });

        it('should not mark as dirty if no changes', async () => {
            await initComponent();

            expect(component.isDirty()).toBe(false);

            // No changes made - isDirty should still be false
            expect(component.isDirty()).toBe(false);
        });

        it('should detect rate limit changes and set isDirty', async () => {
            await initComponent();

            expect(component.isDirty()).toBe(false);

            // Change only rate limit fields
            component.rateLimitRequests.set(999);

            expect(component.isDirty()).toBe(true);
        });

        it('should reset isDirty to false when changes are reverted', async () => {
            await initComponent();

            expect(component.isDirty()).toBe(false);

            // Store the original value
            const originalInstructions = component.settings()!.customInstructions;

            // Make a change to customInstructions
            component.settings.set({ ...component.settings()!, customInstructions: 'Some completely different instructions' });
            expect(component.isDirty()).toBe(true);

            // Revert the change to original
            component.settings.set({ ...component.settings()!, customInstructions: originalInstructions });
            expect(component.isDirty()).toBe(false);
        });
    });

    describe('canDeactivate', () => {
        it('should allow deactivation when not dirty', async () => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
            // No changes made, isDirty should be false
            expect(component.canDeactivate()).toBe(true);
        });

        it('should prevent deactivation when dirty', async () => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
            // Make a change to trigger dirty state
            component.settings.set({ ...component.settings()!, customInstructions: 'Changed' });
            expect(component.canDeactivate()).toBe(false);
        });
    });

    describe('CUSTOM_INSTRUCTIONS_MAX_LENGTH', () => {
        it('should be set to 2048', () => {
            expect(component.CUSTOM_INSTRUCTIONS_MAX_LENGTH).toBe(2048);
        });
    });

    describe('rate limit validation', () => {
        beforeEach(async () => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
        });

        it('should be valid when both fields are empty (use defaults)', async () => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBe(true);
        });

        it('should be valid when both fields are filled with valid values', async () => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(24);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBe(true);
        });

        it('should be valid when requests is 0 and timeframe is positive', async () => {
            component.rateLimitRequests.set(0);
            component.rateLimitTimeframeHours.set(24);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBe(true);
        });

        it('should mark timeframe field as invalid when only requests is filled', async () => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBe('artemisApp.iris.settings.rateLimitValidation.bothRequired');
            expect(component.isFormValid()).toBe(false);
        });

        it('should mark requests field as invalid when only timeframe is filled', async () => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(24);

            expect(component.rateLimitRequestsError()).toBe('artemisApp.iris.settings.rateLimitValidation.bothRequired');
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBe(false);
        });

        it('should mark requests field as invalid when requests is negative', async () => {
            component.rateLimitRequests.set(-1);
            component.rateLimitTimeframeHours.set(24);

            expect(component.rateLimitRequestsError()).toBe('artemisApp.iris.settings.rateLimitValidation.requestsNonNegative');
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBe(false);
        });

        it('should mark timeframe field as invalid when timeframe is zero', async () => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(0);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBe('artemisApp.iris.settings.rateLimitValidation.timeframePositive');
            expect(component.isFormValid()).toBe(false);
        });

        it('should mark timeframe field as invalid when timeframe is negative', async () => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(-1);

            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBe('artemisApp.iris.settings.rateLimitValidation.timeframePositive');
            expect(component.isFormValid()).toBe(false);
        });

        it('should handle empty string as empty value for requests', async () => {
            component.rateLimitRequests.set('' as unknown as number);
            component.rateLimitTimeframeHours.set(undefined);

            // Both effectively empty, should be valid
            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBe(true);
        });

        it('should handle empty string as empty value for timeframe', async () => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set('' as unknown as number);

            // Both effectively empty, should be valid
            expect(component.rateLimitRequestsError()).toBeUndefined();
            expect(component.rateLimitTimeframeError()).toBeUndefined();
            expect(component.isFormValid()).toBe(true);
        });

        it('should mark both fields as invalid when both have errors', async () => {
            component.rateLimitRequests.set(-5);
            component.rateLimitTimeframeHours.set(-3);

            expect(component.rateLimitRequestsError()).toBe('artemisApp.iris.settings.rateLimitValidation.requestsNonNegative');
            expect(component.rateLimitTimeframeError()).toBe('artemisApp.iris.settings.rateLimitValidation.timeframePositive');
            expect(component.isFormValid()).toBe(false);
        });
    });

    describe('effective rate limit preview', () => {
        beforeEach(async () => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
        });

        it('should return application defaults when both fields are empty', async () => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 50, timeframeHours: 12 });
        });

        it('should return entered values when both fields are filled', async () => {
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(48);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 200, timeframeHours: 48 });
        });

        it('should merge entered values with defaults when only requests is filled', async () => {
            component.rateLimitRequests.set(200);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 200, timeframeHours: 12 });
        });

        it('should merge entered values with defaults when only timeframe is filled', async () => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(48);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 50, timeframeHours: 48 });
        });

        it('should handle zero requests as a valid value', async () => {
            component.rateLimitRequests.set(0);
            component.rateLimitTimeframeHours.set(24);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 0, timeframeHours: 24 });
        });

        it('should treat empty string as empty value', async () => {
            component.rateLimitRequests.set('' as unknown as number);
            component.rateLimitTimeframeHours.set('' as unknown as number);

            expect(component.effectiveRateLimitPreview()).toEqual({ requests: 50, timeframeHours: 12 });
        });
    });

    describe('isEffectiveRateLimitUnlimited', () => {
        beforeEach(async () => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
        });

        it('should return false when defaults have values', async () => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);
            // applicationDefaults is { requests: 50, timeframeHours: 12 }

            expect(component.isEffectiveRateLimitUnlimited()).toBe(false);
        });

        it('should return false when explicit values are set', async () => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(24);

            expect(component.isEffectiveRateLimitUnlimited()).toBe(false);
        });

        it('should return true when defaults are null', async () => {
            // Simulate unlimited defaults
            component.applicationDefaults.set({ requests: undefined, timeframeHours: undefined });
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.isEffectiveRateLimitUnlimited()).toBe(true);
        });
    });

    describe('hasEffectiveRequestsLimit and hasEffectiveTimeframeLimit', () => {
        beforeEach(async () => {
            routeParamsSubject.next({ courseId: '1' });
            component.ngOnInit();
            await fixture.whenStable();
        });

        it('should return true when defaults have values and fields are empty', async () => {
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.hasEffectiveRequestsLimit()).toBe(true);
            expect(component.hasEffectiveTimeframeLimit()).toBe(true);
        });

        it('should return true when explicit values are set', async () => {
            component.rateLimitRequests.set(100);
            component.rateLimitTimeframeHours.set(24);

            expect(component.hasEffectiveRequestsLimit()).toBe(true);
            expect(component.hasEffectiveTimeframeLimit()).toBe(true);
        });

        it('should return true for requests when requests is 0', async () => {
            component.rateLimitRequests.set(0);
            component.rateLimitTimeframeHours.set(24);

            expect(component.hasEffectiveRequestsLimit()).toBe(true);
        });

        it('should return false when defaults are null and fields are empty', async () => {
            component.applicationDefaults.set({ requests: undefined, timeframeHours: undefined });
            component.rateLimitRequests.set(undefined);
            component.rateLimitTimeframeHours.set(undefined);

            expect(component.hasEffectiveRequestsLimit()).toBe(false);
            expect(component.hasEffectiveTimeframeLimit()).toBe(false);
        });
    });
});
