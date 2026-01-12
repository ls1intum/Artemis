import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { provideHttpClient } from '@angular/common/http';
import { IrisCourseSettingsDTO, IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';

describe('Iris Settings Service', () => {
    setupTestBed({ zoneless: true });

    let service: IrisSettingsService;
    let httpMock: HttpTestingController;

    const mockCourseSettings: IrisCourseSettingsWithRateLimitDTO = {
        courseId: 1,
        settings: {
            enabled: true,
            customInstructions: 'Test instructions',
            variant: 'default',
            rateLimit: { requests: 100, timeframeHours: 24 },
        },
        effectiveRateLimit: { requests: 100, timeframeHours: 24 },
        applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
    };

    const mockUpdateSettings: IrisCourseSettingsDTO = {
        enabled: false,
        customInstructions: 'Updated instructions',
        variant: 'advanced',
        rateLimit: { requests: 200, timeframeHours: 48 },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisSettingsService],
        });
        service = TestBed.inject(IrisSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        service.clearCache(); // Clear cache between tests
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    describe('getCourseSettingsWithRateLimit', () => {
        it('should get course settings', async () => {
            let result: IrisCourseSettingsWithRateLimitDTO | undefined;
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => (result = resp));

            const req = httpMock.expectOne({ method: 'GET', url: 'api/iris/courses/1/iris-settings' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            expect(result).toEqual(mockCourseSettings);
        });

        it('should reuse pending request when getting course settings', async () => {
            let result1: IrisCourseSettingsWithRateLimitDTO | undefined;
            let result2: IrisCourseSettingsWithRateLimitDTO | undefined;

            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => (result1 = resp));
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => (result2 = resp));

            // Should only trigger one HTTP request
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            expect(result1).toEqual(mockCourseSettings);
            expect(result2).toEqual(mockCourseSettings);
        });

        it('should reuse cached result after getting course settings', async () => {
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            // Second request should use cache, no HTTP request
            let result: IrisCourseSettingsWithRateLimitDTO | undefined;
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => (result = resp));
            httpMock.expectNone({ method: 'GET' });

            expect(result).toEqual(mockCourseSettings);
        });

        it('should trigger new request after cache duration', async () => {
            vi.useFakeTimers();

            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            // Advance time by more than CACHE_DURATION
            vi.advanceTimersByTime((IrisSettingsService as any).CACHE_DURATION + 1);

            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const newReq = httpMock.expectOne({ method: 'GET' });
            newReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
        });

        it('should clear pending request and allow retry after failure', async () => {
            let errorStatus: number | undefined;
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe({
                    error: (err) => (errorStatus = err.status),
                });
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush({}, { status: 500, statusText: 'Internal Server Error' });

            expect(errorStatus).toBe(500);

            // Retry should trigger a new request
            let result: IrisCourseSettingsWithRateLimitDTO | undefined;
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => (result = resp));
            const retryReq = httpMock.expectOne({ method: 'GET' });
            retryReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            expect(result).toEqual(mockCourseSettings);
        });

        it('should handle undefined response body', async () => {
            let result: IrisCourseSettingsWithRateLimitDTO | undefined = mockCourseSettings; // Initialize to something to verify it becomes undefined
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => (result = resp));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(null, { status: 200, statusText: 'Ok' });

            expect(result).toBeUndefined();
        });
    });

    describe('updateCourseSettings', () => {
        it('should update course settings', async () => {
            let responseBody: IrisCourseSettingsWithRateLimitDTO | null | undefined;
            service
                .updateCourseSettings(1, mockUpdateSettings)
                .pipe(take(1))
                .subscribe((resp) => (responseBody = resp.body));

            const req = httpMock.expectOne({ method: 'PUT', url: 'api/iris/courses/1/iris-settings' });
            expect(req.request.body).toEqual(mockUpdateSettings);
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            expect(responseBody).toEqual(mockCourseSettings);
        });

        it('should invalidate cache on successful update', async () => {
            // First, populate the cache
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const getReq = httpMock.expectOne({ method: 'GET' });
            getReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            // Verify cache is populated (no new request)
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            httpMock.expectNone({ method: 'GET' });

            // Update settings
            service.updateCourseSettings(1, mockUpdateSettings).pipe(take(1)).subscribe();
            const putReq = httpMock.expectOne({ method: 'PUT' });
            putReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            // Next getCourseSettings should trigger a new request (cache invalidated)
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const newGetReq = httpMock.expectOne({ method: 'GET' });
            newGetReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
        });
    });

    describe('invalidateCacheForCourse', () => {
        it('should invalidate cache for specific course', async () => {
            // Populate cache for course 1
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const req1 = httpMock.expectOne({ method: 'GET', url: 'api/iris/courses/1/iris-settings' });
            req1.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            // Populate cache for course 2
            service.getCourseSettingsWithRateLimit(2).pipe(take(1)).subscribe();
            const req2 = httpMock.expectOne({ method: 'GET', url: 'api/iris/courses/2/iris-settings' });
            req2.flush({ ...mockCourseSettings, courseId: 2 }, { status: 200, statusText: 'Ok' });

            // Invalidate cache for course 1
            service.invalidateCacheForCourse(1);

            // Course 1 should trigger new request
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const newReq1 = httpMock.expectOne({ method: 'GET', url: 'api/iris/courses/1/iris-settings' });
            newReq1.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            // Course 2 should use cache (no new request)
            service.getCourseSettingsWithRateLimit(2).pipe(take(1)).subscribe();
            httpMock.expectNone({ method: 'GET' });
        });
    });

    describe('clearCache', () => {
        it('should clear all cached settings', async () => {
            // Populate cache
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });

            // Clear cache
            service.clearCache();

            // Should trigger new request
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const newReq = httpMock.expectOne({ method: 'GET' });
            newReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
        });
    });
});
