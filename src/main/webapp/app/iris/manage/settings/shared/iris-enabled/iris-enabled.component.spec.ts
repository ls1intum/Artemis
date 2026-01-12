import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ComponentRef } from '@angular/core';
import { IrisCourseSettingsDTO, IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';

describe('IrisEnabledComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: IrisEnabledComponent;
    let componentRef: ComponentRef<IrisEnabledComponent>;
    let fixture: ComponentFixture<IrisEnabledComponent>;
    let irisSettingsService: IrisSettingsService;

    const course = new Course();
    course.id = 5;

    const mockSettings: IrisCourseSettingsDTO = {
        enabled: true,
        customInstructions: 'Test instructions',
        variant: 'default',
        rateLimit: { requests: 100, timeframeHours: 24 },
    };

    const mockResponse: IrisCourseSettingsWithRateLimitDTO = {
        courseId: 5,
        settings: mockSettings,
        effectiveRateLimit: { requests: 100, timeframeHours: 24 },
        applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
    };

    beforeEach(() => {
        const mockIrisSettingsService = {
            getCourseSettingsWithRateLimit: vi.fn(),
            updateCourseSettings: vi.fn(),
        };

        TestBed.configureTestingModule({
            imports: [IrisEnabledComponent, TranslatePipeMock],
            providers: [provideRouter([]), { provide: IrisSettingsService, useValue: mockIrisSettingsService }, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisEnabledComponent);
        comp = fixture.componentInstance;
        componentRef = fixture.componentRef;
        irisSettingsService = TestBed.inject(IrisSettingsService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockResponse));
        componentRef.setInput('course', course);
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    describe('ngOnInit', () => {
        it('should load course settings on init', async () => {
            const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockResponse));
            componentRef.setInput('course', course);
            fixture.detectChanges();

            expect(getCourseSettingsSpy).toHaveBeenCalledOnce();
            expect(getCourseSettingsSpy).toHaveBeenCalledWith(5);
            await Promise.resolve();
            expect(comp.settings()).toEqual(mockSettings);
            expect(comp.isEnabled()).toBe(true);
            expect(comp.isDisabled()).toBe(false);
        });

        it('should handle undefined response', async () => {
            const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(undefined));
            componentRef.setInput('course', course);
            fixture.detectChanges();

            expect(getCourseSettingsSpy).toHaveBeenCalledOnce();
            await Promise.resolve();
            expect(comp.settings()).toBeUndefined();
            expect(comp.isEnabled()).toBe(false);
            expect(comp.isDisabled()).toBe(true);
        });

        it('should silently fail on error', async () => {
            const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(throwError(() => new Error('Test error')));
            componentRef.setInput('course', course);
            fixture.detectChanges();

            expect(getCourseSettingsSpy).toHaveBeenCalledOnce();
            await Promise.resolve();
            expect(comp.settings()).toBeUndefined();
            expect(comp.isEnabled()).toBe(false);
            expect(comp.isDisabled()).toBe(true);
        });

        it('should not load settings if course has no id', () => {
            const courseWithoutId = new Course();
            const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit');
            componentRef.setInput('course', courseWithoutId);
            fixture.detectChanges();

            expect(getCourseSettingsSpy).not.toHaveBeenCalled();
        });
    });

    describe('setEnabled', () => {
        beforeEach(() => {
            vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockResponse));
            componentRef.setInput('course', course);
            fixture.detectChanges();
        });

        it('should enable Iris and update server', async () => {
            const disabledSettings = { ...mockSettings, enabled: false };
            comp.settings.set(disabledSettings);

            const updateSpy = vi
                .spyOn(irisSettingsService, 'updateCourseSettings')
                .mockReturnValue(of(new HttpResponse({ body: { ...mockResponse, settings: { ...mockSettings, enabled: true } } })));

            comp.setEnabled(true);

            // Optimistic update
            expect(comp.settings()?.enabled).toBe(true);

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledWith(5, { ...disabledSettings, enabled: true });

            await Promise.resolve();
            expect(comp.settings()?.enabled).toBe(true);
            expect(comp.isEnabled()).toBe(true);
        });

        it('should disable Iris and update server', async () => {
            comp.settings.set(mockSettings);

            const updateSpy = vi
                .spyOn(irisSettingsService, 'updateCourseSettings')
                .mockReturnValue(of(new HttpResponse({ body: { ...mockResponse, settings: { ...mockSettings, enabled: false } } })));

            comp.setEnabled(false);

            // Optimistic update
            expect(comp.settings()?.enabled).toBe(false);

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledWith(5, { ...mockSettings, enabled: false });

            await Promise.resolve();
            expect(comp.settings()?.enabled).toBe(false);
            expect(comp.isEnabled()).toBe(false);
        });

        it('should revert on error', async () => {
            comp.settings.set(mockSettings);

            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings').mockReturnValue(throwError(() => new Error('Update failed')));

            comp.setEnabled(false);

            // With synchronous error observable, error handler runs immediately so optimistic update is reverted right away
            expect(updateSpy).toHaveBeenCalledOnce();

            await Promise.resolve();
            // Should be reverted to original state
            expect(comp.settings()?.enabled).toBe(true);
            expect(comp.isEnabled()).toBe(true);
        });

        it('should do nothing if course has no id', () => {
            const courseWithoutId = new Course();
            componentRef.setInput('course', courseWithoutId);
            comp.settings.set(mockSettings);

            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings');

            comp.setEnabled(false);

            expect(updateSpy).not.toHaveBeenCalled();
            expect(comp.settings()).toEqual(mockSettings);
        });

        it('should do nothing if settings are undefined', () => {
            comp.settings.set(undefined);

            const updateSpy = vi.spyOn(irisSettingsService, 'updateCourseSettings');

            comp.setEnabled(false);

            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should keep optimistic update when response body is null', async () => {
            const disabledSettings = { ...mockSettings, enabled: false };
            comp.settings.set(disabledSettings);

            const updateSpy = vi
                .spyOn(irisSettingsService, 'updateCourseSettings')
                .mockReturnValue(of(new HttpResponse({ body: null }) as unknown as HttpResponse<IrisCourseSettingsWithRateLimitDTO>));

            comp.setEnabled(true);

            expect(updateSpy).toHaveBeenCalledOnce();
            await Promise.resolve();
            // Optimistic update should remain since response.body was null
            expect(comp.settings()?.enabled).toBe(true);
        });
    });

    describe('getSettingsRoute', () => {
        it('should return correct route for course settings', () => {
            vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockResponse));
            componentRef.setInput('course', course);
            fixture.detectChanges();

            const route = comp.getSettingsRoute();

            expect(route).toEqual(['/course-management', '5', 'iris-settings']);
        });

        it('should handle course with undefined id', () => {
            const courseWithoutId = new Course();
            componentRef.setInput('course', courseWithoutId);
            // No fixture.detectChanges() call needed since course has no id, ngOnInit won't call service

            const route = comp.getSettingsRoute();

            expect(route).toEqual(['/course-management', 'undefined', 'iris-settings']);
        });
    });

    describe('computed properties', () => {
        it('should compute isEnabled correctly', () => {
            vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockResponse));
            componentRef.setInput('course', course);
            fixture.detectChanges();

            comp.settings.set({ ...mockSettings, enabled: true });
            expect(comp.isEnabled()).toBe(true);

            comp.settings.set({ ...mockSettings, enabled: false });
            expect(comp.isEnabled()).toBe(false);

            comp.settings.set(undefined);
            expect(comp.isEnabled()).toBe(false);
        });

        it('should compute isDisabled correctly', () => {
            vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockResponse));
            componentRef.setInput('course', course);
            fixture.detectChanges();

            comp.settings.set({ ...mockSettings, enabled: true });
            expect(comp.isDisabled()).toBe(false);

            comp.settings.set({ ...mockSettings, enabled: false });
            expect(comp.isDisabled()).toBe(true);

            comp.settings.set(undefined);
            expect(comp.isDisabled()).toBe(true);
        });
    });
});
